package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.catalog.Group
import com.inmc.drops.catalog.Mobs
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 한 종류의 표 목록. 몹·작물은 **게임에서 온 목록 전부**(표가 없으면 열 때 메모리에 생긴다), 블록은 등록한 것만, 공통은 넷.
 * 손으로 만든 파일 가운데 지금 게임에 없는 종류(버전이 내려가 사라진 몹 …)는 "(없는 종류)"로 보인다 — 지우지 않는다.
 */
class TargetListMenu(
    drops: Drops,
    viewer: Player,
    private val category: Category,
    private var page: Int = 0,
    private var onlyConfigured: Boolean = false,
) : Menu(drops, viewer, SIZE, "<dark_gray>${category.label} 표</dark_gray>") {

    override val back: (() -> Unit) = { MainMenu(drops, viewer).show() }

    /** 목록 한 줄. [open] 은 표를 꺼낸다(없으면 메모리에 새로). */
    private class Row(val icon: Material, val name: String, val note: List<String>, val table: DropTable?, val open: () -> DropTable)

    private fun rows(): List<Row> = when (category) {
        Category.MOB -> {
            val known = Mobs.all()
            val ids = known.map(Mobs::id).toSet()
            known.map { type ->
                val id = Mobs.id(type)
                val notes = buildList {
                    if (Mobs.isHostile(type)) add("<red>적대 몹</red>")
                    if (Mobs.isAnimal(type)) add("<green>동물</green>")
                }
                Row(Mobs.icon(type), Mobs.name(type), notes, drops.tables.get(category, id)) { drops.tables.obtain(category, id) }
            } + orphans(ids)
        }
        Category.CROP -> {
            val kinds = drops.crops.all()
            kinds.map { kind ->
                val notes = listOf("<gray>판정: <white>${kind.rule.label}</white></gray>") + if (kind.custom) listOf("<aqua>직접 추가한 작물</aqua>") else emptyList()
                Row(kind.icon, kind.name(), notes, drops.tables.get(category, kind.id)) { drops.tables.obtain(category, kind.id) }
            } + orphans(kinds.map { it.id }.toSet())
        }
        Category.BLOCK -> drops.tables.all(category).map { table ->
            val first = table.targets.firstOrNull()?.let { Material.matchMaterial(it) }
            val notes = listOf("<gray>블록: <white>${table.targets.size}개</white></gray>")
            Row(first?.takeIf { it.isItem } ?: Material.STONE, Labels.table(drops, table), notes, table) { table }
        }
        Category.GROUP -> Group.entries.map { group ->
            Row(group.icon, "<gold>${group.label}</gold>", listOf("<gray>${group.description}</gray>"), drops.tables.get(category, group.id)) {
                drops.tables.obtain(category, group.id)
            }
        }
    }

    /** 파일은 있는데 지금 게임의 목록에 없는 표. */
    private fun orphans(known: Set<String>): List<Row> =
        drops.tables.all(category).filter { it.id !in known }.map { table ->
            Row(Material.BARRIER, "<gray>${table.id}</gray>", listOf("<red>지금 게임에 없는 종류입니다.</red>", "<dark_gray>파일은 지우지 않았습니다.</dark_gray>"), table) { table }
        }

    override fun draw() {
        clear()
        val all = rows().filter { !onlyConfigured || it.table?.isConfigured == true }
        page = Paging.clamp(page, all.size)
        for ((slot, row) in Paging.slice(all, page).withIndex()) {
            set(slot, icon(row)) { TableMenu(drops, viewer, row.open(), back = { TargetListMenu(drops, viewer, category, page, onlyConfigured).show() }).show() }
        }
        fillEmpty(Icon.FILLER)
        pager(page, all.size) { page = it; refresh() }

        if (category == Category.MOB || category == Category.CROP) {
            set(SLOT_FILTER, Icon.of(if (onlyConfigured) Material.ENDER_EYE else Material.ENDER_PEARL,
                if (onlyConfigured) "<green>설정된 것만 보는 중</green>" else "<gray>전부 보는 중</gray>",
                "<gray>항목이 있거나 설정을 바꾼 표만 봅니다.</gray>", "", "<yellow>▶ 클릭해서 바꾸기</yellow>")) {
                onlyConfigured = !onlyConfigured
                page = 0
                refresh()
            }
        }
        if (category == Category.BLOCK || category == Category.CROP) {
            val what = if (category == Category.BLOCK) "블록" else "작물"
            set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>$what 추가</green>",
                if (category == Category.BLOCK) "<gray>블록 아이템을 빈 칸에 올리고 확인 — 블록마다 표가 생깁니다.</gray>"
                else "<gray>목록에 없는 블록을 작물로 넣습니다(규칙은 블록을 보고 정합니다).</gray>",
                "<dark_gray>올린 아이템은 돌려드립니다.</dark_gray>")) {
                AddTargetsMenu(drops, viewer, category, null) { TargetListMenu(drops, viewer, category, page, onlyConfigured).show() }.show()
            }
        }
        navigation()
    }

    private fun icon(row: Row): ItemStack {
        val table = row.table
        val lore = buildList {
            addAll(row.note)
            if (table == null || table.entries.isEmpty()) add("<dark_gray>비어 있음</dark_gray>") else add("<gray>항목: <white>${table.entries.size}개</white></gray>")
            if (table != null && !table.enabled) add("<red>꺼짐</red>")
            if (table != null && table.bonus > 0.0) add("<gray>보너스: <white>레벨당 +${table.bonus}%</white></gray>")
            add("")
            add("<yellow>▶ 클릭: 표 열기</yellow>")
        }
        val stack = Icon.of(row.icon, row.name, lore)
        if (table?.isConfigured == true) stack.editMeta { it.setEnchantmentGlintOverride(true) }
        return stack
    }

    companion object {
        const val SIZE = 54
        const val SLOT_FILTER = 49
        const val SLOT_ADD = 50
    }
}
