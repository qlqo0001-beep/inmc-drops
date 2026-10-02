package com.inmc.drops

import com.inmc.drops.catalog.CropRule
import com.inmc.drops.config.DropsConfig
import com.inmc.drops.config.WorldRule
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropEntry
import com.inmc.drops.table.DropTable
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StorageMode
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 표·설정이 저장했다 읽어도 그대로인가. 파일을 거쳐(문자열로) 확인한다. */
class StorageTest {

    private fun reload(yaml: YamlConfiguration): YamlConfiguration = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }

    @Test
    fun `표가 저장했다 읽어도 그대로다 — 항목 순서까지`() {
        val table = DropTable(Category.CROP, "my_crop", listOf("SWEET_BERRY_BUSH", "sweet_berry_bush"), CropRule.BERRIES)
        table.enabled = false
        table.bonus = 25.0
        table.entries += DropEntry(
            id = "b0000001",
            item = StoredItem(ItemRef.parse("inmc:미확인_부여서"), Material.ENCHANTED_BOOK, StorageMode.REFERENCE, displayName = "미확인 부여서"),
            chance = 1.5, autoChance = 0.25, minAmount = 1, maxAmount = 3,
            announce = true, hidden = true, commands = mutableListOf("say {플레이어네임}"), giveItem = true,
        )
        table.entries += DropEntry(id = "a0000002", item = StoredItem(ItemRef.parse("minecraft:paper"), Material.PAPER), chance = 50.0, giveItem = false, commands = mutableListOf("eco give {player} 10"))

        val again = DropTable.load(Category.CROP, "my_crop", reload(table.save()))!!
        assertEquals(listOf("sweet_berry_bush"), again.targets, "대상은 소문자 하나로")
        assertEquals(CropRule.BERRIES, again.rule)
        assertEquals(false, again.enabled)
        assertEquals(25.0, again.bonus)
        assertEquals(listOf("b0000001", "a0000002"), again.entries.map { it.id }, "id 순이 아니라 넣은 순서")
        val first = again.entries[0]
        assertEquals(table.entries[0].item, first.item)
        assertEquals(1.5, first.chance)
        assertEquals(0.25, first.autoChance)
        assertEquals(3, first.maxAmount)
        assertTrue(first.announce && first.hidden && first.giveItem)
        assertEquals(listOf("say {플레이어네임}"), first.commands)
        assertEquals(false, again.entries[1].giveItem)
        assertEquals(0.0, again.entries[1].autoChance)
    }

    @Test
    fun `숫자처럼 보이는 항목 id 도 그대로 돌아온다`() {
        // id 는 UUID 의 앞 8자(16진수)라 숫자만으로 된 것도 나온다 — YAML 이 키를 8진수·정수로 읽으면 통계와 이어지지 않는다.
        val ids = listOf("00123456", "12345678", "08123456", "1e500000", "0x1f2e3d")
        val table = DropTable(Category.MOB, "zombie")
        for (id in ids) table.entries += DropEntry(id = id, item = StoredItem(ItemRef.parse("minecraft:paper"), Material.PAPER))
        val again = DropTable.load(Category.MOB, "zombie", reload(table.save()))!!
        assertEquals(ids, again.entries.map { it.id })
    }

    @Test
    fun `다른 종류의 폴더에 옮겨진 표는 읽지 않는다`() {
        val table = DropTable(Category.BLOCK, "stone", listOf("stone"))
        assertNull(DropTable.load(Category.MOB, "stone", reload(table.save())))
    }

    @Test
    fun `아이템 칸이 빈 줄은 빠지고 나머지는 산다`() {
        val yaml = YamlConfiguration()
        yaml.set("category", "mob")
        yaml.set("entries.broken.chance", 10.0)
        yaml.set("entries.ok.item", "minecraft:paper")
        yaml.set("entries.ok.chance", 10.0)
        val table = DropTable.load(Category.MOB, "zombie", reload(yaml))!!
        assertEquals(listOf("ok"), table.entries.map { it.id })
    }

    @Test
    fun `설정이 저장했다 읽어도 그대로다 — 점이 든 월드 이름까지`() {
        val config = DropsConfig(
            defaultChance = 2.5, protectSeconds = 0, autoCap = DropsConfig.UNLIMITED,
            worlds = mapOf("resource.world" to WorldRule(mob = true, crop = false, block = false), "world_nether" to WorldRule(mob = false)),
        )
        val again = DropsConfig.from(reload(config.toYaml()))
        assertEquals(config, again)
        assertEquals(false, again.allows("resource.world", Category.BLOCK))
        assertEquals(true, again.allows("어디든", Category.BLOCK), "적지 않은 월드는 켜짐")
    }

    @Test
    fun `월드를 기본값으로 되돌리면 목록에서 빠진다`() {
        val off = DropsConfig().withWorld("w", WorldRule(block = false))
        assertEquals(setOf("w"), off.worlds.keys)
        assertEquals(emptyMap(), off.withWorld("w", WorldRule()).worlds)
    }
}
