package com.inmc.drops

import com.inmc.drops.gui.AddTargetsMenu
import com.inmc.drops.gui.BoostMenu
import com.inmc.drops.gui.EntryMenu
import com.inmc.drops.gui.InfoListMenu
import com.inmc.drops.gui.InfoMainMenu
import com.inmc.drops.gui.InfoMenu
import com.inmc.drops.gui.MainMenu
import com.inmc.drops.gui.SettingsMenu
import com.inmc.drops.gui.TableMenu
import com.inmc.drops.gui.TableSettingsMenu
import com.inmc.drops.gui.TargetEditMenu
import com.inmc.drops.gui.TargetListMenu
import com.inmc.drops.gui.WorldMenu
import kr.inmc.core.gui.Paging
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 슬롯 상수 — 겹치면 나중에 그린 버튼만 보이고 **안 보이는 버튼의 클릭이 남는다**. 범위 밖은 `Menu.set` 이 조용히 버린다.
 * 컴파일러가 못 잡는 것이라 여기서 본다. 54칸 화면은 core [Paging] 의 아래 줄(뒤로 45·이전 46·다음 47·닫기 53)과도 겹치면 안 된다.
 */
class MenuLayoutTest {

    private fun slots(type: Class<*>): Map<String, Int> =
        type.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("SLOT_") && it.type == Int::class.javaPrimitiveType }
            .associate { it.isAccessible = true; it.name to it.getInt(null) }

    private fun check(type: Class<*>, size: Int, paging: Boolean = false, contentEnd: Int = 0) {
        val map = slots(type)
        assertTrue(map.isNotEmpty(), type.simpleName)
        for ((name, slot) in map) {
            assertTrue(slot in 0 until size, "${type.simpleName}.$name = $slot 은 $size 칸 밖")
            assertTrue(slot >= contentEnd, "${type.simpleName}.$name = $slot 이 목록 칸(0~${contentEnd - 1})을 덮는다")
        }
        val all = map.entries.map { it.key to it.value } + if (paging) listOf(
            "Paging.SLOT_BACK" to Paging.SLOT_BACK, "Paging.SLOT_PREV" to Paging.SLOT_PREV, "Paging.SLOT_NEXT" to Paging.SLOT_NEXT,
        ) else emptyList()
        val clashes = all.groupBy { it.second }.filter { it.value.size > 1 }
        assertTrue(clashes.isEmpty(), "${type.simpleName} 겹침: $clashes")
    }

    @Test
    fun `화면마다 슬롯이 범위 안이고 겹치지 않는다`() {
        check(MainMenu::class.java, MainMenu.SIZE)
        check(TargetListMenu::class.java, TargetListMenu.SIZE, paging = true, contentEnd = Paging.PER_PAGE)
        check(AddTargetsMenu::class.java, AddTargetsMenu.SIZE, contentEnd = AddTargetsMenu.INPUT_END)
        check(TableMenu::class.java, TableMenu.SIZE, paging = true, contentEnd = TableMenu.CONTENT_SIZE)
        check(EntryMenu::class.java, EntryMenu.SIZE)
        check(TableSettingsMenu::class.java, TableSettingsMenu.SIZE)
        check(TargetEditMenu::class.java, TargetEditMenu.SIZE, paging = true, contentEnd = TargetEditMenu.CONTENT_SIZE)
        check(SettingsMenu::class.java, SettingsMenu.SIZE)
        check(WorldMenu::class.java, WorldMenu.SIZE)
        check(BoostMenu::class.java, BoostMenu.SIZE)
        // "없음"(22)은 목록이 비었을 때만 그린다 — 목록 칸과 겹쳐도 된다.
        check(InfoMenu::class.java, InfoMenu.SIZE, paging = true)
        check(InfoMainMenu::class.java, InfoMainMenu.SIZE)
        check(InfoListMenu::class.java, InfoListMenu.SIZE, paging = true)
    }

    @Test
    fun `닫기 버튼(53)은 54칸 목록 화면의 다른 버튼과 겹치지 않는다`() {
        for (type in listOf(TargetListMenu::class.java, TargetEditMenu::class.java, InfoMenu::class.java, InfoListMenu::class.java)) {
            assertTrue(Paging.SLOT_CLOSE !in slots(type).values, type.simpleName)
        }
    }
}
