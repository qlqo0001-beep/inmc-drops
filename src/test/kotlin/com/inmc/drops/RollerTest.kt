package com.inmc.drops

import com.inmc.drops.roll.Roller
import com.inmc.drops.table.DropEntry
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RollerTest {

    private fun entry(chance: Double, min: Int = 1, max: Int = 1, giveItem: Boolean = true) =
        DropEntry(item = StoredItem(ItemRef.parse("minecraft:paper"), Material.PAPER), chance = chance, minAmount = min, maxAmount = max, giveItem = giveItem)

    @Test
    fun `최종 확률 = 기본 × (1 + 보너스 × 레벨) × 배율, 0~100`() {
        assertEquals(10.0, Roller.chance(10.0, 0.0, 3, 1.0), 1e-9)
        // 10% 항목에 레벨당 50% — 행운 III 이면 10 × 2.5 = 25%.
        assertEquals(25.0, Roller.chance(10.0, 50.0, 3, 1.0), 1e-9)
        assertEquals(50.0, Roller.chance(10.0, 50.0, 3, 2.0), 1e-9)
        // 배율이 이미 흔한 항목을 100% 넘게 만들지 않는다.
        assertEquals(100.0, Roller.chance(80.0, 0.0, 0, 3.0), 1e-9)
        // 0% 는 배율이 붙어도 0 — "잠깐 끄기" 가 이벤트에 풀리지 않게.
        assertEquals(0.0, Roller.chance(0.0, 100.0, 3, 10.0), 1e-9)
        // 음수 레벨은 없는 것으로.
        assertEquals(10.0, Roller.chance(10.0, 50.0, -2, 1.0), 1e-9)
    }

    @Test
    fun `100 은 늘 나오고 0 은 안 나오며 아무것도 안 하는 줄은 굴리지 않는다`() {
        val always = entry(100.0)
        val never = entry(0.0)
        val empty = entry(100.0, giveItem = false)
        val rng = Random(42)
        repeat(200) {
            val rolled = Roller.roll(listOf(always, never, empty), { it.chance }, rng)
            assertEquals(listOf(always), rolled.map { it.entry })
        }
    }

    @Test
    fun `확률대로 나온다`() {
        val half = entry(50.0)
        val rng = Random(7)
        val hits = (1..10_000).count { Roller.roll(listOf(half), { it.chance }, rng).isNotEmpty() }
        assertTrue(hits in 4_700..5_300, "50% 가 $hits / 10000")
    }

    @Test
    fun `개수는 최소~최대 안에서 양끝을 포함한다`() {
        val ranged = entry(100.0, min = 2, max = 4)
        val rng = Random(1)
        val seen = (1..500).map { Roller.amount(ranged, rng) }.toSet()
        assertEquals(setOf(2, 3, 4), seen)
        assertEquals(3, Roller.amount(entry(100.0, min = 3, max = 3), rng))
    }

    @Test
    fun `평균 n번에 1번`() {
        assertEquals(50L, Roller.oneIn(2.0))
        assertEquals(1L, Roller.oneIn(100.0))
        assertEquals(10_000L, Roller.oneIn(0.01))
        assertNull(Roller.oneIn(0.0))
    }

    @Test
    fun `항목의 값은 범위를 지킨다`() {
        val e = entry(150.0, min = 0, max = 1000)
        assertEquals(100.0, e.chance)
        assertEquals(1, e.minAmount)
        assertEquals(DropEntry.MAX_AMOUNT, e.maxAmount)
        e.autoChance = -5.0
        assertEquals(0.0, e.autoChance)
        e.minAmount = 10
        assertTrue(e.maxAmount >= 10)
        e.maxAmount = 3
        assertEquals(3, e.minAmount)
    }
}
