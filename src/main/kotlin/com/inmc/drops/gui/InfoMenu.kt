package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * `/드랍 정보` 의 마지막 화면 — 몹·작물·블록 하나에서 무엇이 나오는지([InfoListMenu] 에서 고른 것). 읽기만 한다. 숨긴 항목은 안 보인다.
 *
 * 확률은 표에 적힌 기본값이다(행운·약탈 보너스는 줄로 따로 알린다). 드랍 이벤트 중이면 그 배율도 알린다.
 */
class InfoMenu(
    drops: Drops,
    viewer: Player,
    name: String,
    private val tables: List<DropTable>,
    /** 이 월드에서 이 종류가 꺼져 있다. */
    private val off: Boolean,
    override val back: (() -> Unit)? = null,
    private var page: Int = 0,
) : Menu(drops, viewer, SIZE, "<dark_gray>드랍 정보 <gray>|</gray> </dark_gray>$name") {

    private class Line(val table: DropTable, val entry: com.inmc.drops.table.DropEntry)

    override fun draw() {
        clear()
        val lines = tables.flatMap { table -> visible(table).map { Line(table, it) } }
        page = Paging.clamp(page, lines.size)
        for ((slot, line) in Paging.slice(lines, page).withIndex()) {
            val entry = line.entry
            val icon = drops.resolver.icon(entry.item).stack
            val amount = if (entry.minAmount == entry.maxAmount) "${entry.minAmount}개" else "${entry.minAmount}~${entry.maxAmount}개"
            set(slot, Icon.annotate(icon, lore = buildList {
                addAll(TableMenu.chanceLines(entry, TableMenu.isCropTable(line.table)))
                add("<gray>수량: <white>$amount</white></gray>")
                if (line.table.bonus > 0.0) add("<gray>${TableMenu.bonusName(line.table)} 레벨당 <white>+${Numbers.chance(line.table.bonus)}%</white></gray>")
                add("<dark_gray>출처: </dark_gray>" + Labels.table(drops, line.table))
            }))
        }
        if (lines.isEmpty()) set(SLOT_EMPTY, Icon.of(Material.GRAY_DYE, "<gray>커스텀 드랍이 없습니다.</gray>"))
        fillEmpty(Icon.FILLER)
        if (off) {
            set(SLOT_NOTE, Icon.of(Material.BARRIER, "<red>이 월드에서는 꺼져 있습니다.</red>", "<gray>여기서는 위의 것이 나오지 않습니다.</gray>"))
        } else if (drops.boost.active()) {
            set(SLOT_NOTE, Icon.of(Material.NETHER_STAR, "<gold>드랍 이벤트 ×${Numbers.chance(drops.boost.multiplier)}</gold>",
                "<gray>지금은 위의 확률에 이 배율이 곱해집니다.</gray>", "<gray>남은 시간: <white>${drops.boost.remainingText()}</white></gray>"))
        }
        pager(page, lines.size) { page = it; refresh() }
        navigation()
    }

    companion object {

        /** 정보에 보일 항목 — 켜진 표의, 숨기지 않았고 확률이 있는 것. */
        fun visible(table: DropTable): List<com.inmc.drops.table.DropEntry> =
            if (!table.enabled) emptyList() else table.entries.filter { !it.hidden && it.chance + it.autoChance > 0.0 }

        const val SIZE = 54
        const val SLOT_EMPTY = 22
        const val SLOT_NOTE = 49
    }
}
