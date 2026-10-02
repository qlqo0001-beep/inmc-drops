package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.table.DropEntry
import com.inmc.drops.table.DropTable
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 항목 하나의 설정 — urb 의 보상 상세(`gui/RewardDetailMenu.kt`)와 같은 칸에 셋을 더했다:
 * 자동 농사 확률(작물 표만), 정보에서 숨기기, 지금까지 나온 횟수.
 */
class EntryMenu(
    drops: Drops,
    viewer: Player,
    private val table: DropTable,
    private val entry: DropEntry,
    override val back: () -> Unit,
) : Menu(drops, viewer, SIZE, "<dark_gray>항목 설정</dark_gray>") {

    private val usesAuto = TableMenu.isCropTable(table)

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)
        set(SLOT_PREVIEW, preview())

        // --- 사람 확률 -----------------------------------------------------------------------
        chanceButton(SLOT_STEP_SMALLEST, 0.01)
        chanceButton(SLOT_STEP_SMALL, 0.1)
        chanceButton(SLOT_STEP_ONE, 1.0)
        chanceButton(SLOT_STEP_TEN, 10.0)
        set(SLOT_CHANCE, Icon.of(Material.WRITABLE_BOOK, "<yellow>확률 직접 입력</yellow>",
            "<gray>지금: <yellow>${Numbers.chance(entry.chance)}%</yellow></gray>",
            "<dark_gray>0 ~ 100, 소수점 두 자리. 0 = 잠깐 끄기</dark_gray>", "", "<yellow>▶ 클릭</yellow>")) {
            ask(DialogForm("<yellow>확률</yellow>").decimal("chance", "확률(%)", entry.chance, 0.0, 100.0)) { v ->
                v.decimal("chance")?.let { entry.chance = it; save() }
            }
        }

        // --- 자동 농사 확률(작물만) --------------------------------------------------------------
        if (usesAuto) {
            set(SLOT_AUTO, Icon.of(Material.PISTON, "<yellow>자동 농사 확률</yellow>",
                "<gray>지금: <yellow>${Numbers.chance(entry.autoChance)}%</yellow></gray>" + if (entry.autoChance <= 0.0) " <dark_gray>(안 나옴)</dark_gray>" else "",
                "<gray>피스톤·물·주민·받침 무너짐으로 캔 것.</gray>",
                "<gray>사람이 캔 것과 따로 정합니다(보통 더 낮게).</gray>",
                "<dark_gray>청크마다 1시간 상한은 설정에서.</dark_gray>",
                "", "<yellow>▶ 좌클릭: 입력  /  우클릭: 0 (안 나옴)</yellow>")) { event ->
                if (event.isRightClick) {
                    entry.autoChance = 0.0
                    save()
                    refresh()
                    return@set
                }
                ask(DialogForm("<yellow>자동 농사 확률</yellow>").decimal("chance", "확률(%)", entry.autoChance, 0.0, 100.0)) { v ->
                    v.decimal("chance")?.let { entry.autoChance = it; save() }
                }
            }
        }

        set(SLOT_HIDDEN, toggleIcon("정보에서 숨기기", entry.hidden, "<gray>켜면 /드랍 정보 에 안 보입니다(숨겨 둔 희귀 드랍).</gray>")) {
            entry.hidden = !entry.hidden
            save()
            refresh()
        }

        // --- 개수 ------------------------------------------------------------------------------
        set(SLOT_MIN, Icon.of(Material.IRON_NUGGET, "<yellow>최소 개수</yellow>",
            "<gray>지금: <white>${entry.minAmount}개</white></gray>", "", "<yellow>▶ 좌클릭 +1 / 우클릭 -1 · Shift 로 ±10</yellow>")) { event ->
            entry.minAmount += Editors.step(event, 1)
            save()
            refresh()
        }
        set(SLOT_MAX, Icon.of(Material.GOLD_NUGGET, "<yellow>최대 개수</yellow>",
            "<gray>지금: <white>${entry.maxAmount}개</white></gray>", "", "<yellow>▶ 좌클릭 +1 / 우클릭 -1 · Shift 로 ±10</yellow>")) { event ->
            entry.maxAmount += Editors.step(event, 1)
            save()
            refresh()
        }

        // --- 참조 플러그인 -----------------------------------------------------------------------
        val candidates = drops.resolver.candidates(entry.item)
        set(SLOT_SOURCE, Icon.of(if (candidates.size > 1) Material.SPYGLASS else Material.GRAY_DYE, "<yellow>참조 플러그인</yellow>", buildList {
            add("<gray>지금: <white>${refLabel(entry.item.ref)}</white></gray>")
            add("<dark_gray>${entry.item.ref.serialize()}</dark_gray>")
            if (candidates.size > 1) {
                add("")
                add("<gray>이 아이템을 <white>${candidates.size}개</white> 플러그인이 알아봅니다.</gray>")
                candidates.forEach { add((if (it == entry.item.ref) "<green>▶</green> " else "<dark_gray>· </dark_gray>") + "<dark_gray>${refLabel(it)} - ${it.serialize()}</dark_gray>") }
                add("")
                add("<yellow>▶ 좌클릭: 다음  /  우클릭: 이전</yellow>")
            }
        })) { event ->
            if (candidates.size <= 1) return@set
            val index = candidates.indexOf(entry.item.ref)
            val next = if (event.isRightClick) candidates[(index - 1 + candidates.size) % candidates.size] else candidates[(index + 1) % candidates.size]
            entry.item = entry.item.copy(ref = next)
            save()
            refresh()
        }

        // --- 저장 방식 ---------------------------------------------------------------------------
        val reference = entry.item.mode == StorageMode.REFERENCE
        val canReference = entry.item.ref != ItemRef.None
        set(SLOT_MODE, Icon.of(if (reference) Material.RECOVERY_COMPASS else Material.BUNDLE,
            if (reference) "<green>저장 방식: 참조 (자동 갱신)</green>" else "<yellow>저장 방식: 스냅샷 (고정)</yellow>", buildList {
                if (reference) {
                    add("<gray>떨굴 때마다 원본 정의대로 새로 만듭니다.</gray>")
                    add("<gray>커스텀아이템·MMOItems 에서 고치면 그대로 반영됩니다.</gray>")
                    add("<gray>미확인 부여서처럼 매번 새로 만드는 아이템은 이것으로.</gray>")
                } else {
                    add("<gray>등록할 때의 아이템을 그대로 떨굽니다.</gray>")
                }
                add("")
                add(if (canReference) "<yellow>▶ 클릭하여 바꾸기</yellow>" else "<red>이 아이템은 참조로 나타낼 수 없습니다.</red>")
            })) {
            if (!canReference) return@set
            entry.item = entry.item.withMode(entry.item.mode.toggle())
            save()
            refresh()
        }

        set(SLOT_ANNOUNCE, toggleIcon("나오면 서버 공지", entry.announce, "<gray>누가 어디서 얻었는지 모두에게 알립니다.</gray>")) {
            entry.announce = !entry.announce
            save()
            refresh()
        }

        set(SLOT_COMMANDS, Icon.of(Material.COMMAND_BLOCK, "<yellow>실행 명령어</yellow>", buildList {
            if (entry.commands.isEmpty()) add("<gray>명령어가 없습니다.</gray>") else entry.commands.forEach { add("<dark_gray>/$it</dark_gray>") }
            add("<dark_gray>콘솔이 실행합니다. {플레이어네임} = 얻은 사람.</dark_gray>")
            add("<dark_gray>자동 농사로 나온 것은 명령어를 돌리지 않습니다.</dark_gray>")
            add("")
            add("<gray>아이템 떨구기: </gray>" + Icon.toggle(entry.giveItem))
            add("")
            add("<yellow>▶ 좌클릭: 명령어 편집 ( | 로 여러 개, 비우면 지움)</yellow>")
            add("<yellow>▶ 우클릭: 아이템 떨구기 켜고 끄기</yellow>")
        })) { event ->
            if (event.isRightClick) {
                entry.giveItem = !entry.giveItem
                save()
                refresh()
                return@set
            }
            ask(DialogForm("<yellow>실행 명령어</yellow>").line("<gray>예: give {플레이어네임} diamond 1|say {플레이어네임} 축하합니다</gray>")
                .text("commands", "명령어( | 로 여러 개)", entry.commands.joinToString("|"), maxLength = 2000)) { v ->
                entry.commands = v.text("commands").split('|').map { it.trim().removePrefix("/") }.filter { it.isNotEmpty() }.toMutableList()
                save()
            }
        }

        set(SLOT_STATS, Icon.of(Material.CLOCK, "<yellow>나온 횟수</yellow>",
            "<gray>지금까지 <white>${drops.stats.get(table.category, table.id, entry.id)}번</white></gray>",
            "<dark_gray>검증으로 나온 것은 세지 않습니다.</dark_gray>", "", "<red>▶ Shift+클릭: 0 으로</red>")) { event ->
            if (!event.isShiftClick) return@set
            drops.stats.reset(table.category, table.id, entry.id)
            refresh()
        }

        set(SLOT_BACK, Icon.back()) { back() }
        set(SLOT_DELETE, Icon.of(Material.TNT, "<red>이 항목 지우기</red>", "<gray>표에서 뺍니다.</gray>", "", "<red>▶ Shift+클릭</red>")) { event ->
            if (!event.isShiftClick) return@set
            table.entries.remove(entry)
            save()
            back()
        }
    }

    private fun chanceButton(slot: Int, delta: Double) {
        set(slot, Icon.of(Material.LIGHT_BLUE_DYE, "<aqua>확률 ±${Numbers.chance(delta)}%</aqua>",
            "<gray>지금: <yellow>${Numbers.chance(entry.chance)}%</yellow></gray>", "",
            "<yellow>▶ 좌클릭: +${Numbers.chance(delta)}</yellow>", "<yellow>▶ 우클릭: -${Numbers.chance(delta)}</yellow>")) { event ->
            entry.chance += if (event.isLeftClick) delta else -delta
            save()
            refresh()
        }
    }

    private fun preview() = drops.resolver.icon(entry.item).let { icon ->
        Icon.annotate(icon.stack, lore = TableMenu.chanceLines(entry, usesAuto) +
            listOf("<gray>수량: <white>${entry.minAmount} ~ ${entry.maxAmount}개</white></gray>", "<dark_gray>${entry.item.ref.serialize()}</dark_gray>") + icon.notes())
    }

    /** 참조가 어느 플러그인 것인지 관리자의 말로. */
    private fun refLabel(ref: ItemRef): String = when (ref) {
        is ItemRef.MMOItems -> "MMOItems"
        is ItemRef.Namespaced -> when (ref.namespace) {
            "inmc" -> "커스텀아이템"
            "itemsadder" -> "ItemsAdder"
            else -> ref.namespace
        }
        is ItemRef.Vanilla -> "바닐라"
        is ItemRef.None -> "스냅샷 전용"
    }

    private fun save() = drops.tables.markDirty(table)

    companion object {
        const val SIZE = 45
        const val SLOT_PREVIEW = 4
        const val SLOT_STEP_SMALLEST = 19
        const val SLOT_STEP_SMALL = 20
        const val SLOT_STEP_ONE = 21
        const val SLOT_STEP_TEN = 22
        const val SLOT_CHANCE = 23
        const val SLOT_AUTO = 24
        const val SLOT_HIDDEN = 25
        const val SLOT_SOURCE = 28
        const val SLOT_MIN = 29
        const val SLOT_MAX = 30
        const val SLOT_STATS = 31
        const val SLOT_MODE = 32
        const val SLOT_ANNOUNCE = 33
        const val SLOT_COMMANDS = 34
        const val SLOT_BACK = 36
        const val SLOT_DELETE = 44
    }
}
