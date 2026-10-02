package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.catalog.CropRule
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import com.inmc.drops.util.Ph
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player

/** 표 하나의 설정 — 켜고 끄기, 행운·약탈 보너스, (블록) 대상 블록, (직접 추가한 작물) 판정 규칙, 표 지우기. */
class TableSettingsMenu(
    drops: Drops,
    viewer: Player,
    private val table: DropTable,
    override val back: () -> Unit,
) : Menu(drops, viewer, SIZE, "<dark_gray>표 설정 <gray>|</gray> </dark_gray>" + Labels.table(drops, table)) {

    override fun draw() {
        clear()
        set(SLOT_ENABLED, toggleIcon("켜짐", table.enabled, "<gray>끄면 항목을 지우지 않고 이 표만 쉽니다.</gray>")) {
            table.enabled = !table.enabled
            drops.tables.markDirty(table)
            refresh()
        }

        val bonus = TableMenu.bonusName(table)
        set(SLOT_BONUS, valueIcon(Material.EXPERIENCE_BOTTLE, "$bonus 보너스", "레벨당 +${Numbers.chance(table.bonus)}%",
            "<gray>$bonus 레벨 하나마다 확률에 곱하는 몫.</gray>",
            "<gray>예: 10% 항목에 50 이면 $bonus III 에서 25%.</gray>",
            "<dark_gray>0 = 안 올림. 자동 농사에는 붙지 않습니다.</dark_gray>")) {
            ask(DialogForm("<yellow>$bonus 보너스</yellow>").decimal("bonus", "레벨당 %", table.bonus, 0.0, DropTable.MAX_BONUS)) { v ->
                v.decimal("bonus")?.let { table.bonus = it; drops.tables.markDirty(table) }
            }
        }

        when {
            table.category == Category.BLOCK -> set(SLOT_TARGETS, Icon.of(Material.GRASS_BLOCK, "<yellow>대상 블록</yellow>", buildList {
                for (target in table.targets) add("<gray>· </gray>" + (Material.matchMaterial(target)?.let(Labels::of) ?: target))
                add("")
                add("<yellow>▶ 클릭: 넣기·빼기</yellow>")
            })) { TargetEditMenu(drops, viewer, table) { TableSettingsMenu(drops, viewer, table, back).show() }.show() }

            table.category == Category.CROP && table.rule != null -> {
                val rule = table.rule ?: CropRule.NATURAL
                set(SLOT_TARGETS, Icon.of(Material.WHEAT_SEEDS, "<yellow>판정 규칙: <white>${rule.label}</white></yellow>",
                    listOf("<gray>${rule.description}</gray>", "") + Editors.optionList(CropRule.entries, rule) { it.label } + Editors.cycleHint)) { event ->
                    table.rule = Editors.cycle(event, CropRule.entries, rule)
                    drops.tables.markDirty(table)
                    refresh()
                }
            }

            table.category == Category.CROP -> drops.crops.byId(table.id)?.let { kind ->
                set(SLOT_TARGETS, Icon.of(Material.WHEAT_SEEDS, "<yellow>판정 규칙: <white>${kind.rule.label}</white></yellow>",
                    "<gray>${kind.rule.description}</gray>", "<dark_gray>기본 작물이라 규칙은 고정입니다.</dark_gray>"))
            }
        }

        if (table.category == Category.BLOCK || (table.category == Category.CROP && table.rule != null)) {
            set(SLOT_DELETE, Icon.of(Material.TNT, "<red>이 표 지우기</red>", "<gray>항목까지 전부 사라집니다.</gray>", "", "<red>▶ Shift+클릭</red>")) { event ->
                if (!event.isShiftClick) return@set
                drops.tables.delete(table)
                drops.messages.send(viewer, "table-deleted", Ph.of().source(Labels.table(drops, table)))
                TargetListMenu(drops, viewer, table.category).show()
            }
        }

        fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    companion object {
        const val SIZE = 27
        const val SLOT_ENABLED = 10
        const val SLOT_BONUS = 12
        const val SLOT_TARGETS = 14
        const val SLOT_DELETE = 16
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26
    }
}

/** 블록 표의 대상 블록 — 눌러서 빼고, [추가]로 넣는다. 마지막 하나는 뺄 수 없다(표를 지울 것). */
class TargetEditMenu(
    drops: Drops,
    viewer: Player,
    private val table: DropTable,
    override val back: () -> Unit,
) : Menu(drops, viewer, SIZE, "<dark_gray>대상 블록</dark_gray>") {

    override fun draw() {
        clear()
        for ((slot, target) in table.targets.take(CONTENT_SIZE).withIndex()) {
            val material = Material.matchMaterial(target)
            set(slot, Icon.of(material?.takeIf { it.isItem } ?: Material.BARRIER, material?.let(Labels::of) ?: target,
                "<dark_gray>$target</dark_gray>", "", "<red>▶ 클릭: 빼기</red>")) {
                if (table.targets.size <= 1) {
                    drops.messages.send(viewer, "last-target")
                    return@set
                }
                table.targets.remove(target)
                drops.tables.markDirty(table)
                refresh()
            }
        }
        fillEmpty(Icon.FILLER)
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>블록 넣기</green>", "<gray>블록 아이템을 올려 이 표에 더합니다.</gray>")) {
            AddTargetsMenu(drops, viewer, Category.BLOCK, table) { TargetEditMenu(drops, viewer, table, back).show() }.show()
        }
        navigation()
    }

    companion object {
        const val SIZE = 54
        const val CONTENT_SIZE = 45
        const val SLOT_ADD = 49
    }
}
