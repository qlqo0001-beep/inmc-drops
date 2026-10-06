package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.boost.Boost
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player

/** 드랍 이벤트 — 시작·상태·끝내기. `/드랍 배율` 과 같은 일을 한다. */
class BoostMenu(drops: Drops, viewer: Player) : Menu(drops, viewer, SIZE, "<dark_gray>드랍 이벤트</dark_gray>") {

    override val back: () -> Unit = { MainMenu(drops, viewer).show() }

    override fun draw() {
        clear()
        val boost = drops.boost
        set(SLOT_START, Icon.of(Material.FIREWORK_ROCKET, "<green>이벤트 시작</green>",
            "<gray>모든 커스텀 드랍 확률에 배율을 곱합니다(자동 농사 포함).</gray>",
            "<gray>커스텀 몬스터 드랍에도 같이 걸립니다.</gray>",
            "<gray>보스바로 남은 시간이 보이고, 재시작해도 이어집니다.</gray>",
            "<dark_gray>진행 중이면 새 값으로 바꿉니다.</dark_gray>", "", "<yellow>▶ 클릭</yellow>")) {
            ask(DialogForm("<gold>드랍 이벤트</gold>")
                .decimal("multiplier", "배율(예: 2 = 두 배)", if (boost.active()) boost.multiplier else 2.0, Boost.MIN, Boost.MAX)
                .long("minutes", "시간(분)", 60, 1, Boost.MAX_MINUTES)) { v ->
                val multiplier = v.decimal("multiplier") ?: return@ask
                val minutes = v.long("minutes") ?: return@ask
                boost.startAnnounced(multiplier, minutes)
            }
        }
        set(SLOT_STATUS, Icon.of(if (boost.active()) Material.NETHER_STAR else Material.GRAY_DYE,
            if (boost.active()) "<gold>진행 중 ×${Numbers.chance(boost.multiplier)}</gold>" else "<gray>진행 중인 이벤트 없음</gray>",
            if (boost.active()) "<gray>남은 시간: <white>${boost.remainingText()}</white></gray>" else "<dark_gray>왼쪽에서 시작합니다.</dark_gray>"))
        set(SLOT_STOP, Icon.of(Material.BARRIER, "<red>이벤트 끝내기</red>", "<gray>바로 끝내고 보스바를 내립니다.</gray>")) {
            val stopped = boost.stop()
            drops.messages.send(viewer, if (stopped) "boost-stopped" else "boost-none")
            refresh()
        }
        fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    companion object {
        const val SIZE = 27
        const val SLOT_START = 11
        const val SLOT_STATUS = 13
        const val SLOT_STOP = 15
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26
    }
}
