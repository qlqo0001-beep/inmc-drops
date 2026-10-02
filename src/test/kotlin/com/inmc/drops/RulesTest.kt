package com.inmc.drops

import com.inmc.drops.catalog.CropRule
import com.inmc.drops.catalog.Crops
import com.inmc.drops.catalog.Group
import com.inmc.drops.roll.AutoCap
import com.inmc.drops.table.BlockTwins
import com.inmc.drops.track.Packing
import kr.inmc.core.store.DefinitionKey
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 작물 규칙·자동 농사 상한·위치 묶기·광석 쌍둥이·카탈로그 — 서버 없이. */
class RulesTest {

    @Test
    fun `다 자람은 나이가 최대일 때만`() {
        assertFalse(CropRule.AGE_MAX.ready(0, 7, null))
        assertFalse(CropRule.AGE_MAX.ready(6, 7, null))
        assertTrue(CropRule.AGE_MAX.ready(7, 7, null))
        assertFalse(CropRule.AGE_MAX.ready(null, null, null), "나이가 없는 블록은 다 자랄 수 없다")
    }

    @Test
    fun `열매는 열매 속성이 있으면 그것, 없으면 달콤한 열매의 나이`() {
        assertTrue(CropRule.BERRIES.ready(null, null, true))
        assertFalse(CropRule.BERRIES.ready(25, 25, false), "발광 열매 덩굴은 나이가 아니라 열매 속성")
        assertFalse(CropRule.BERRIES.ready(1, 3, null))
        assertTrue(CropRule.BERRIES.ready(CropRule.SWEET_BERRY_RIPE, 3, null))
        assertTrue(CropRule.BERRIES.ready(3, 3, null))
    }

    @Test
    fun `자연 생성은 규칙으로는 언제나 — 놓은 블록은 청크 기록이 따로 본다`() {
        assertTrue(CropRule.NATURAL.ready(0, 15, null))
        assertTrue(CropRule.NATURAL.ready(null, null, null))
    }

    @Test
    fun `규칙은 이름으로 저장하고 읽는다`() {
        for (rule in CropRule.entries) assertEquals(rule, CropRule.parse(rule.id))
        assertEquals(null, CropRule.parse("AGE_MAX"))
    }

    @Test
    fun `자동 농사 상한 — 음수는 무제한, 0 은 늘 막힘, 창 안에서 센다`() {
        val cap = AutoCap(windowMillis = 1000)
        assertTrue(cap.allows("w:0:0", 0, -1))
        assertFalse(cap.allows("w:0:0", 0, 0))
        assertTrue(cap.allows("w:0:0", 0, 3))
        cap.add("w:0:0", 0, 2)
        assertTrue(cap.allows("w:0:0", 10, 3))
        cap.add("w:0:0", 10, 1)
        assertFalse(cap.allows("w:0:0", 20, 3))
        // 다른 청크는 따로.
        assertTrue(cap.allows("w:1:0", 20, 3))
        // 창이 지나면 처음부터.
        assertTrue(cap.allows("w:0:0", 1000, 3))
        assertEquals(0, cap.count("w:0:0", 1000))
    }

    @Test
    fun `지난 창은 버린다`() {
        val cap = AutoCap(windowMillis = 1000)
        cap.add("a", 0, 5)
        cap.add("b", 900, 5)
        cap.prune(1000)
        assertEquals(0, cap.count("a", 1000))
        assertEquals(5, cap.count("b", 1000))
    }

    @Test
    fun `위치 묶기 — 청크 하나의 모든 자리가 서로 다르고 되돌릴 수 있다`() {
        val seen = HashSet<Int>()
        for (y in -64..319) for (x in 0..15) for (z in 0..15) {
            val packed = Packing.pack(x, y, z)
            assertEquals(y, Packing.y(packed))
            seen += packed
        }
        assertEquals(384 * 16 * 16, seen.size)
        // 음수 좌표는 그 청크 안의 자리로 — x = -1 은 왼쪽 청크의 15 번째 칸.
        val negative = Packing.pack(-1, -64, -17)
        assertEquals(15, Packing.localX(negative))
        assertEquals(15, Packing.localZ(negative))
        assertEquals(-64, Packing.y(negative))
    }

    @Test
    fun `광석은 심층암 쌍둥이와 같이, 광석이 아니면 혼자`() {
        assertEquals(listOf(Material.DEEPSLATE_DIAMOND_ORE), BlockTwins.of(Material.DIAMOND_ORE))
        assertEquals(listOf(Material.IRON_ORE), BlockTwins.of(Material.DEEPSLATE_IRON_ORE))
        assertEquals(emptyList(), BlockTwins.of(Material.NETHER_GOLD_ORE))
        assertEquals(emptyList(), BlockTwins.of(Material.STONE))
        assertEquals(emptyList(), BlockTwins.of(Material.ANCIENT_DEBRIS))
    }

    @Test
    fun `기본 작물 — 블록이 겹치지 않고 줄기·후렴초가 없고 id 가 파일 이름이 된다`() {
        val materials = Crops.BUILT_IN.flatMap { it.materials }
        assertEquals(materials.size, materials.toSet().size)
        assertTrue(materials.none { it.name.endsWith("_STEM") })
        assertTrue(Material.CHORUS_PLANT !in materials && Material.CHORUS_FLOWER !in materials)
        assertTrue(Crops.BUILT_IN.all { DefinitionKey.isValid(it.id) })
        assertEquals(Crops.BUILT_IN.size, Crops.BUILT_IN.map { it.id }.toSet().size)
        // 놓은 것으로 복사가 되는 작물은 전부 자연 생성 규칙이다.
        for (m in listOf(Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO, Material.KELP)) {
            assertEquals(CropRule.NATURAL, Crops.BUILT_IN.single { m in it.materials }.rule, m.name)
        }
    }

    @Test
    fun `카탈로그는 기본 작물을 블록으로 찾는다`() {
        val crops = Crops()
        assertEquals("glow_berries", crops.of(Material.CAVE_VINES_PLANT)?.id)
        assertEquals("kelp", crops.of(Material.KELP_PLANT)?.id)
        assertEquals(null, crops.of(Material.STONE))
        assertTrue(Material.SUGAR_CANE in crops.naturalMaterials())
        assertFalse(Material.WHEAT in crops.naturalMaterials())
    }

    @Test
    fun `공통 표 id 는 파일 이름이 된다`() {
        assertTrue(Group.entries.all { DefinitionKey.isValid(it.id) })
        assertEquals(Group.entries.size, Group.entries.map { it.id }.toSet().size)
    }
}
