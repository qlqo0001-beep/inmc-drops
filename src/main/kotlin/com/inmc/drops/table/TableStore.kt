package com.inmc.drops.table

import com.inmc.drops.Drops
import com.inmc.drops.catalog.CropKind
import com.inmc.drops.catalog.CropRule
import com.inmc.drops.catalog.Group
import com.inmc.drops.catalog.Mobs
import kr.inmc.core.store.YamlFolder
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType

/**
 * 드랍 표 전부. 종류마다 폴더 하나(`tables/mob/`, `tables/crop/`, `tables/block/`, `tables/group/`).
 *
 * **편집한 표만 파일이 생긴다.** 몹·작물의 목록은 게임(카탈로그)에서 오므로, 화면에서 열기만 한 표는 메모리에만 있다가
 * 관리자가 무언가 바꾸면([markDirty]) 그때 쓰인다. 그래서 "모든 몹 자동 등록"이 빈 파일 80개 없이 된다.
 *
 * 메인 스레드에서만 고친다. 파일 쓰기는 core [YamlFolder] 가 dirty 인 것만 워커에서.
 */
class TableStore(private val drops: Drops) {

    private val folders: Map<Category, YamlFolder> = Category.entries.associateWith {
        YamlFolder(drops.io, drops.logger, "tables/" + it.id, HEADER, "드랍 표(${it.label})")
    }

    private val tables: Map<Category, LinkedHashMap<String, DropTable>> = Category.entries.associateWith { LinkedHashMap() }

    @Volatile
    private var blockIndex: Map<Material, DropTable> = emptyMap()

    /** 놓은 블록 기록이 따라다닐 블록 — 블록 표의 대상 + 자연 생성 작물. */
    @Volatile
    var tracked: Set<Material> = emptySet()
        private set

    /** 켤 때 — 그 자리에서 읽는다(첫 사건 전에 표가 있어야 한다). */
    fun loadNow() {
        apply(Category.entries.associateWith { read(it) })
    }

    /** 리로드 — 파일은 워커에서 읽고 반영은 메인에서. */
    fun reload(then: () -> Unit) {
        flushBlocking()
        drops.io.async({ Category.entries.associateWith { read(it) } }) { loaded ->
            apply(loaded)
            then()
        }
    }

    private fun read(category: Category): List<DropTable> =
        folders.getValue(category).readAll { id, config -> DropTable.load(category, id, config) }.map { it.second }

    private fun apply(loaded: Map<Category, List<DropTable>>) {
        for ((category, list) in loaded) {
            val map = tables.getValue(category)
            map.clear()
            for (table in list) map[table.id] = table
        }
        reindex()
    }

    fun all(category: Category): Collection<DropTable> = tables.getValue(category).values

    fun get(category: Category, id: String): DropTable? = tables.getValue(category)[id]

    fun configuredCount(): Int = Category.entries.sumOf { c -> all(c).count { it.isConfigured } }

    /** 있으면 그것, 없으면 메모리에만 새로(파일은 [markDirty] 때). */
    fun obtain(category: Category, id: String, targets: List<String> = emptyList(), rule: CropRule? = null): DropTable =
        tables.getValue(category).getOrPut(id) { DropTable(category, id, targets, rule) }

    fun markDirty(table: DropTable) {
        tables.getValue(table.category)[table.id] = table
        folders.getValue(table.category).markDirty(table.id)
        reindex()
    }

    fun delete(table: DropTable) {
        tables.getValue(table.category).remove(table.id)
        folders.getValue(table.category).deleteFile(table.id)
        reindex()
    }

    /** 메모리에만 넣고 색인을 다시 짠다(파일은 안 쓴다) — 검증기의 임시 표. 끝나면 [discard]. */
    fun putTemporary(table: DropTable) {
        tables.getValue(table.category)[table.id] = table
        reindex()
    }

    /** 메모리에서만 뺀다(파일은 그대로) — 검증기가 만든 임시 표. */
    fun discard(table: DropTable) {
        tables.getValue(table.category).remove(table.id)
        folders.getValue(table.category).forget(table.id)
        reindex()
    }

    // --- 사건이 찾는 것 ------------------------------------------------------------------

    fun block(material: Material): DropTable? = blockIndex[material]

    /** 몹이 죽었을 때 굴릴 표 — 그 몹의 표 + 맞는 공통 표. */
    fun forMob(type: EntityType): List<DropTable> =
        listOfNotNull(get(Category.MOB, Mobs.id(type))) +
            Group.entries.filter { it.kind == Category.MOB && it.appliesTo(type) }.mapNotNull { get(Category.GROUP, it.id) }

    /** 작물을 캤을 때 굴릴 표 — 그 작물의 표 + 모든 작물 표. */
    fun forCrop(kind: CropKind): List<DropTable> =
        listOfNotNull(get(Category.CROP, kind.id), get(Category.GROUP, Group.CROP_ALL.id))

    fun isTracked(material: Material): Boolean = material in tracked

    /** 블록 → 표 색인, 관리자가 추가한 작물, 기록할 블록을 다시 짠다. 표를 바꿀 때마다. */
    fun reindex() {
        val index = HashMap<Material, DropTable>()
        for (table in all(Category.BLOCK)) {
            for (target in table.targets) {
                val material = Material.matchMaterial(target) ?: continue
                val taken = index.putIfAbsent(material, table)
                if (taken != null && taken !== table) {
                    drops.logger.warning("블록 '$target' 이(가) 표 '${taken.id}' 와 '${table.id}' 에 둘 다 있습니다 — '${taken.id}' 만 씁니다.")
                }
            }
        }
        blockIndex = index
        drops.crops.refresh(customCrops())
        tracked = index.keys + drops.crops.naturalMaterials()
    }

    private fun customCrops(): List<CropKind> = all(Category.CROP).mapNotNull { table ->
        val rule = table.rule ?: return@mapNotNull null
        val materials = table.targets.mapNotNull { Material.matchMaterial(it) }.ifEmpty { return@mapNotNull null }
        CropKind(table.id, materials, rule, materials.first().takeIf { it.isItem } ?: Material.WHEAT_SEEDS, custom = true)
    }

    // --- 저장 ------------------------------------------------------------------------------

    fun flush() {
        for ((category, folder) in folders) folder.flushDirty { id -> render(category, id) }
    }

    fun flushBlocking() {
        for ((category, folder) in folders) folder.flushDirtyBlocking { id -> render(category, id) }
    }

    private fun render(category: Category, id: String): YamlConfiguration? = get(category, id)?.save()

    companion object {
        const val HEADER = "inmc-drops 드랍 표. 게임 안 /드랍 에서 고치세요 — 서버가 켜져 있는 동안 손으로 고치면 덮어씁니다."
    }
}
