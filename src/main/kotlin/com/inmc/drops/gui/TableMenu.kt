package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.catalog.Group
import com.inmc.drops.roll.Roller
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropEntry
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import com.inmc.drops.util.Ph
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack

/**
 * 표 하나의 항목 목록 — urb 의 보상 등록 화면(`gui/RewardListMenu.kt`)과 같은 조작.
 *
 * 등록 모드: 빈 칸에 아이템을 올리고 확인. 제거 모드: 항목을 클릭해 빼낸 뒤 확인. 항목 위에서 Q·F·Shift+우클릭은 상세 설정.
 * 등록한 아이템은 urb 처럼 **화면이 가져간다**(표에 그 모습이 저장된다).
 */
class TableMenu(
    drops: Drops,
    viewer: Player,
    private val table: DropTable,
    private var page: Int = 0,
    private var removeMode: Boolean = false,
    override val back: () -> Unit,
) : Menu(drops, viewer, SIZE, "<dark_gray>드랍 표 <gray>|</gray> </dark_gray>" + Labels.table(drops, table)) {

    private val slotToEntry = HashMap<Int, DropEntry>()
    private val stagedRemovals = HashSet<Int>()

    /** 자동 농사 확률을 쓰는 표 — 작물 표와 모든 작물 표. */
    private val usesAuto = isCropTable(table)

    override fun draw() {
        clear()
        slotToEntry.clear()
        val entries = table.entries
        val pages = Paging.pageCount(entries.size, CONTENT_SIZE)
        page = page.coerceIn(0, pages - 1)

        Paging.slice(entries, page, CONTENT_SIZE).forEachIndexed { index, entry ->
            slotToEntry[index] = entry
            val icon = entryIcon(entry)
            set(index, if (removeMode && index in stagedRemovals) null else icon) { event ->
                if (removeMode) {
                    // 아이콘은 보여 주는 사본이라 사람에게 주지 않는다 — 빼내기만 하고, 빈 칸을 다시 누르면 되돌린다.
                    if (!stagedRemovals.add(index)) stagedRemovals.remove(index)
                    inventory.setItem(index, if (index in stagedRemovals) null else icon)
                    viewer.updateInventory()
                    return@set
                }
                when (event.click) {
                    ClickType.DROP, ClickType.CONTROL_DROP, ClickType.SWAP_OFFHAND, ClickType.SHIFT_RIGHT ->
                        EntryMenu(drops, viewer, table, entry) { reopen() }.show()
                    else -> Unit
                }
            }
        }

        for (slot in CONTENT_SIZE until SIZE) set(slot, Icon.EDGE)

        set(Paging.SLOT_BACK, Icon.back()) {
            returnStaged()
            back()
        }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { switchPage(page - 1) }
        if (page < pages - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { switchPage(page + 1) }

        set(SLOT_SCALE, Icon.of(Material.COMPARATOR, "<yellow>확률 일괄 조정</yellow>", scaleLore())) { event ->
            if (table.entries.isEmpty()) return@set
            returnStaged()
            if (event.isRightClick) askSpread() else askScale()
        }

        set(SLOT_MODE, Icon.of(
            if (removeMode) Material.LAVA_BUCKET else Material.HOPPER,
            if (removeMode) "<red>제거 모드 (켜짐)</red>" else "<gray>제거 모드 (꺼짐)</gray>",
            if (removeMode) {
                listOf("<gray>지울 항목을 클릭해 빼낸 뒤</gray>", "<gray>확인을 누르면 지워집니다.</gray>", "<dark_gray>다시 클릭하면 되돌립니다.</dark_gray>", "", "<yellow>▶ 클릭하여 등록 모드로</yellow>")
            } else {
                listOf("<gray>빈 칸에 아이템을 올린 뒤</gray>", "<gray>확인을 누르면 등록됩니다.</gray>", "", "<yellow>▶ 클릭하여 제거 모드로</yellow>")
            },
        )) {
            returnStaged()
            TableMenu(drops, viewer, table, page, !removeMode, back).show()
        }

        set(SLOT_SETTINGS, Icon.of(Material.REDSTONE_TORCH, "<yellow>표 설정</yellow>",
            "<gray>켜짐: </gray>" + Icon.toggle(table.enabled),
            "<gray>${bonusName(table)} 보너스: <white>레벨당 +${Numbers.chance(table.bonus)}%</white></gray>",
            "", "<yellow>▶ 클릭</yellow>")) {
            returnStaged()
            TableSettingsMenu(drops, viewer, table) { reopen() }.show()
        }

        set(SLOT_HELP, Icon.of(Material.PAPER, "<yellow>도움말</yellow>", buildList {
            add("<gray>등록된 항목: <white>${table.entries.size}개</white></gray>")
            add("<gray>새 항목의 확률: <white>${Numbers.chance(drops.config.defaultChance)}%</white></gray>")
            add("<gray>항목마다 따로 굴립니다 — 여러 개가 같이 나올 수 있습니다.</gray>")
            if (usesAuto) {
                add("")
                add("<gray>자동 농사(피스톤·물·주민) 확률은 항목마다 따로입니다.</gray>")
                add("<gray>처음에는 0 — 자동 농사에서는 안 나옵니다.</gray>")
            }
            add("")
            add("<yellow>Q / F / Shift+우클릭</yellow><gray> : 확률·개수·공지·명령어</gray>")
        }))

        set(SLOT_CONFIRM, Icon.confirm(
            if (removeMode) "<red>✔ 제거 확정</red>" else "<green>✔ 등록 확정</green>",
            listOf("<gray>바꾼 것을 저장합니다.</gray>"),
        )) {
            if (removeMode) applyRemovals() else applyAdditions()
            reopen()
        }
    }

    override fun isSlotEditable(slot: Int): Boolean {
        if (slot >= CONTENT_SIZE) return false
        // 제거 모드는 진짜 아이템을 내주지 않는다.
        return !removeMode && !slotToEntry.containsKey(slot)
    }

    override fun acceptsShiftInsert(): Boolean = !removeMode

    override fun onClose(event: InventoryCloseEvent) = returnStaged()

    /** 항목이 아닌 칸에 올린 것을 전부 항목으로. 아이템은 화면이 가져간다(urb 와 같다). */
    private fun applyAdditions() {
        var added = 0
        for (slot in 0 until CONTENT_SIZE) {
            if (slotToEntry.containsKey(slot)) continue
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir) continue
            table.entries += DropEntry(
                item = drops.resolver.capture(stack),
                chance = drops.config.defaultChance,
                minAmount = stack.amount,
                maxAmount = stack.amount,
            )
            inventory.setItem(slot, null)
            added++
        }
        if (added > 0) {
            drops.tables.markDirty(table)
            drops.messages.send(viewer, "entries-added", Ph.of().count(added))
        }
    }

    private fun applyRemovals() {
        val removed = stagedRemovals.mapNotNull { slotToEntry[it] }
        stagedRemovals.clear()
        if (removed.isEmpty()) return
        table.entries.removeAll(removed.toSet())
        drops.tables.markDirty(table)
        drops.messages.send(viewer, "entries-removed", Ph.of().count(removed.size))
    }

    /** 확인하지 않고 올려 둔 것을 돌려준다. */
    private fun returnStaged() {
        for (slot in 0 until CONTENT_SIZE) {
            if (slotToEntry.containsKey(slot)) continue
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir) continue
            inventory.setItem(slot, null)
            viewer.inventory.addItem(stack).values.forEach { viewer.world.dropItemNaturally(viewer.location, it) }
        }
    }

    private fun switchPage(target: Int) {
        returnStaged()
        TableMenu(drops, viewer, table, target, removeMode, back).show()
    }

    private fun reopen() = TableMenu(drops, viewer, table, page, removeMode, back).show()

    // --- 확률 일괄 조정 -----------------------------------------------------------------------

    /**
     * 표 전체를 한 번에. 비율 유지(좌클릭)는 지금의 희귀도 차이를 그대로 두고 전체 크기만 바꾼다 — "너무 잘 나온다"에 맞는 손.
     * 균등 분배(우클릭)는 그것을 버린다. 사람 확률만 바꾼다(자동 농사 확률은 항목마다).
     */
    private fun scaleLore(): List<String> {
        if (table.entries.isEmpty()) return listOf("<red>등록된 항목이 없습니다.</red>")
        val sum = table.entries.sumOf { it.chance }
        return listOf(
            "<gray>확률 합: <white>${Numbers.chance(sum)}%</white>  <dark_gray>(${table.entries.size}종)</dark_gray></gray>",
            "<gray>한 번에 평균 <white>${Numbers.chance(sum / 100.0)}개</white> 나옵니다.</gray>",
            "",
            "<yellow>▶ 좌클릭: 목표 합으로 비율 유지 조정</yellow>",
            "<red>▶ 우클릭: 전부 같은 확률로</red>",
        )
    }

    private fun askScale() {
        val sum = table.entries.sumOf { it.chance }
        ask(DialogForm("<yellow>비율 유지 조정</yellow>")
            .line("<gray>지금 합: <white>${Numbers.chance(sum)}%</white> — 예: 50 이면 한 번에 평균 0.5개</gray>")
            .decimal("target", "목표 확률 합(%)", sum, 0.01, 100.0 * table.entries.size), reopen = { reopen() }) { v ->
            val target = v.decimal("target") ?: return@ask
            if (sum <= 0.0) return@ask
            val factor = target / sum
            table.entries.forEach { it.chance = it.chance * factor }
            drops.tables.markDirty(table)
        }
    }

    private fun askSpread() {
        ask(DialogForm("<red>균등 분배</red>")
            .line("<red>지금의 항목별 확률은 모두 사라집니다.</red>")
            .decimal("target", "목표 확률 합(%)", 100.0, 0.01, 100.0 * table.entries.size), reopen = { reopen() }) { v ->
            val target = v.decimal("target") ?: return@ask
            val each = target / table.entries.size
            table.entries.forEach { it.chance = each }
            drops.tables.markDirty(table)
        }
    }

    private fun entryIcon(entry: DropEntry): ItemStack {
        val icon = drops.resolver.icon(entry.item)
        val amount = if (entry.minAmount == entry.maxAmount) "${entry.minAmount}개" else "${entry.minAmount}~${entry.maxAmount}개"
        val lore = buildList {
            addAll(chanceLines(entry, usesAuto))
            add("<gray>수량: <white>$amount</white></gray>")
            add("<gray>저장 방식: <white>${if (entry.item.mode == StorageMode.REFERENCE) "참조 (자동 갱신)" else "스냅샷 (고정)"}</white></gray>")
            add("<dark_gray>${entry.item.ref.serialize()}</dark_gray>")
            if (entry.announce) add("<gold>★ 나오면 서버 공지</gold>")
            if (entry.hidden) add("<dark_purple>정보에서 숨김</dark_purple>")
            if (entry.commands.isNotEmpty()) add("<aqua>명령어 ${entry.commands.size}개</aqua>")
            if (!entry.giveItem) add("<dark_gray>아이템 없음 (명령어만)</dark_gray>")
            add("<gray>지금까지 <white>${drops.stats.get(table.category, table.id, entry.id)}번</white> 나왔습니다.</gray>")
            addAll(icon.notes())
            add("")
            add(if (removeMode) "<red>▶ 클릭하여 빼낸 뒤 확인</red>" else "<yellow>▶ Q / F / Shift+우클릭: 상세 설정</yellow>")
        }
        return Icon.annotate(icon.stack, lore = lore)
    }

    companion object {
        const val SIZE = 54
        const val CONTENT_SIZE = 45
        const val SLOT_SCALE = 48
        const val SLOT_MODE = 49
        const val SLOT_SETTINGS = 50
        const val SLOT_HELP = 51
        const val SLOT_CONFIRM = 53

        fun isCropTable(table: DropTable): Boolean = table.category == Category.CROP || table.id == Group.CROP_ALL.id

        /** 표의 보너스가 따르는 마법부여 — 몹은 약탈, 그 밖은 행운. */
        fun bonusName(table: DropTable): String =
            if (table.category == Category.MOB || (table.category == Category.GROUP && Group.of(table.id)?.kind == Category.MOB)) "약탈" else "행운"

        /** 확률 줄 — 작물이면 사람·자동 농사 둘, "평균 n번에 1번" 까지. */
        fun chanceLines(entry: DropEntry, usesAuto: Boolean): List<String> = buildList {
            add("<gray>확률: <yellow>${Numbers.chance(entry.chance)}%</yellow></gray>" + (Roller.oneIn(entry.chance)?.let { " <dark_gray>(평균 ${it}번에 1번)</dark_gray>" } ?: ""))
            if (usesAuto) {
                add("<gray>자동 농사: <yellow>${Numbers.chance(entry.autoChance)}%</yellow></gray>" + if (entry.autoChance <= 0.0) " <dark_gray>(안 나옴)</dark_gray>" else "")
            }
        }
    }
}
