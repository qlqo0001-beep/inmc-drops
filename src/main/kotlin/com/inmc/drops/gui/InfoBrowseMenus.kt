package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.catalog.Group
import com.inmc.drops.catalog.Mobs
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * `/드랍 정보` 첫 화면 — 관리 화면처럼 **몹 · 작물 · 블록**으로 나눠 들어간다. 읽기만 한다.
 */
class InfoMainMenu(drops: Drops, viewer: Player) : Menu(drops, viewer, SIZE, "<dark_gray>드랍 정보</dark_gray>") {

    override fun draw() {
        clear()
        category(SLOT_MOBS, Material.ZOMBIE_HEAD, Category.MOB, "플레이어가 직접 처치했을 때 나옵니다.")
        category(SLOT_CROPS, Material.WHEAT, Category.CROP, "다 자란 작물을 캘 때 나옵니다.")
        category(SLOT_BLOCKS, Material.DIAMOND_ORE, Category.BLOCK, "캘 때 나옵니다(놓은 블록·섬세한 손길은 안 나옴).")
        if (drops.boost.active()) {
            set(SLOT_NOTE, Icon.of(Material.NETHER_STAR, "<gold>드랍 이벤트 ×${Numbers.chance(drops.boost.multiplier)}</gold>",
                "<gray>지금은 모든 확률에 이 배율이 곱해집니다.</gray>", "<gray>남은 시간: <white>${drops.boost.remainingText()}</white></gray>"))
        }
        fillEmpty(Icon.FILLER)
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun category(slot: Int, material: Material, category: Category, note: String) {
        val count = InfoListMenu.rows(drops, category).size
        val lore = buildList {
            add("<gray>$note</gray>")
            add("")
            add(if (count > 0) "<gray>커스텀 드랍이 있는 것: <white>${count}개</white></gray>" else "<dark_gray>커스텀 드랍이 없습니다.</dark_gray>")
            if (!drops.config.allows(viewer.world.name, category)) add("<red>이 월드에서는 꺼져 있습니다.</red>")
            add("<yellow>▶ 클릭</yellow>")
        }
        set(slot, Icon.of(material, "<green>${category.label}</green>", lore)) { InfoListMenu(drops, viewer, category).show() }
    }

    companion object {
        const val SIZE = 27
        const val SLOT_MOBS = 11
        const val SLOT_CROPS = 13
        const val SLOT_BLOCKS = 15
        const val SLOT_NOTE = 22
        const val SLOT_CLOSE = 26
    }
}

/**
 * 한 종류에서 **커스텀 드랍이 있는 것 전부**. 맨 앞은 공통 표(모든 몹·적대 몹·동물 / 모든 작물), 그 뒤는 자기 표에 보이는 항목이 있는 몹·작물·블록.
 * 누르면 그것에서 나오는 것 전부([InfoMenu] — 몹·작물은 공통 표의 것까지).
 */
class InfoListMenu(
    drops: Drops,
    viewer: Player,
    private val category: Category,
    private var page: Int = 0,
) : Menu(drops, viewer, SIZE, "<dark_gray>드랍 정보 <gray>|</gray> ${category.label}</dark_gray>") {

    override val back: (() -> Unit) = { InfoMainMenu(drops, viewer).show() }

    /** 목록 한 줄 — 그림, 이름, 덧붙일 줄, 누르면 보일 표들. */
    class Row(val icon: Material, val name: String, val notes: List<String>, val tables: List<DropTable>)

    override fun draw() {
        clear()
        val all = rows(drops, category)
        page = Paging.clamp(page, all.size)
        val off = !drops.config.allows(viewer.world.name, category)
        for ((slot, row) in Paging.slice(all, page).withIndex()) {
            val count = row.tables.sumOf { InfoMenu.visible(it).size }
            val lore = row.notes + listOf("<gray>커스텀 드랍: <white>${count}가지</white></gray>", "", "<yellow>▶ 클릭: 보기</yellow>")
            set(slot, Icon.of(row.icon, row.name, lore)) {
                InfoMenu(drops, viewer, row.name, row.tables, off, back = { InfoListMenu(drops, viewer, category, page).show() }).show()
            }
        }
        if (all.isEmpty()) set(SLOT_EMPTY, Icon.of(Material.GRAY_DYE, "<gray>${category.label}에서 나오는 커스텀 드랍이 없습니다.</gray>"))
        fillEmpty(Icon.FILLER)
        if (off) set(SLOT_NOTE, Icon.of(Material.BARRIER, "<red>이 월드에서는 꺼져 있습니다.</red>", "<gray>여기서는 위의 것이 나오지 않습니다.</gray>"))
        pager(page, all.size) { page = it; refresh() }
        navigation()
    }

    companion object {
        const val SIZE = 54
        const val SLOT_EMPTY = 22
        const val SLOT_NOTE = 49

        fun rows(drops: Drops, category: Category): List<Row> {
            fun shows(table: DropTable?) = table != null && InfoMenu.visible(table).isNotEmpty()
            val groups = Group.entries.filter { it.kind == category }.mapNotNull { group ->
                val table = drops.tables.get(Category.GROUP, group.id)?.takeIf(::shows) ?: return@mapNotNull null
                Row(group.icon, "<gold>공통 · ${group.label}</gold>", listOf("<gray>${group.description}</gray>"), listOf(table))
            }
            val own = when (category) {
                Category.MOB -> Mobs.all().filter { shows(drops.tables.get(Category.MOB, Mobs.id(it))) }.map { type ->
                    val notes = buildList {
                        if (Mobs.isHostile(type)) add("<red>적대 몹</red>")
                        if (Mobs.isAnimal(type)) add("<green>동물</green>")
                    }
                    Row(Mobs.icon(type), Mobs.name(type), notes, drops.tables.forMob(type))
                }
                Category.CROP -> drops.crops.all().filter { shows(drops.tables.get(Category.CROP, it.id)) }.map { kind ->
                    Row(kind.icon, kind.name(), emptyList(), drops.tables.forCrop(kind))
                }
                Category.BLOCK -> drops.tables.all(Category.BLOCK).filter(::shows).sortedBy { it.id }.map { table ->
                    val blocks = table.targets.mapNotNull { Material.matchMaterial(it) }
                    val first = blocks.firstOrNull()
                    val notes = if (blocks.size > 1) listOf("<gray>블록: </gray>" + blocks.take(4).joinToString("<gray>, </gray>") { "<white>${Labels.of(it)}</white>" } +
                        if (blocks.size > 4) " <gray>외 ${blocks.size - 4}</gray>" else "") else emptyList()
                    Row(first?.takeIf { it.isItem } ?: Material.STONE, Labels.table(drops, table), notes, listOf(table))
                }
                Category.GROUP -> emptyList()
            }
            return groups + own
        }
    }
}
