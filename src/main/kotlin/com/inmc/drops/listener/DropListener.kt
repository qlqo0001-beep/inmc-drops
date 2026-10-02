package com.inmc.drops.listener

import com.destroystokyo.paper.event.block.BlockDestroyEvent
import com.inmc.drops.Drops
import com.inmc.drops.catalog.CropKind
import com.inmc.drops.catalog.CropRule
import com.inmc.drops.catalog.Mobs
import com.inmc.drops.roll.Roller
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropTable
import io.papermc.paper.event.block.BlockBreakBlockEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.type.CaveVinesPlant
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.bukkit.persistence.PersistentDataType

/**
 * 드랍을 굴리는 곳. 전부 **MONITOR + `ignoreCancelled`** — 보호 플러그인(Lands·WorldGuard …)이 막은 파괴·처치는 여기 오지 않는다.
 * 그래서 보호 플러그인과 따로 연동하지 않는다.
 *
 * | 사건 | 누가 | 확률 |
 * |---|---|---|
 * | [onDeath] | 플레이어가 처치한 몹(커스텀 몹 빼고) | 사람 확률 × 약탈 |
 * | [onBreak] | 플레이어가 캔 블록·작물 | 사람 확률 × 행운 |
 * | [onHarvest] | 열매 우클릭 수확 | 사람 확률 |
 * | [onBreakBlock]·[onDestroy] | 피스톤·물·받침 무너짐·주민 — 자동 농사 | 자동 농사 확률 |
 *
 * 같은 틱에 같은 자리는 **한 번만** 센다([firstThisTick]) — 사람이 캔 블록에 다른 파괴 사건이 따라와도 두 번 주지 않는다.
 */
class DropListener(private val drops: Drops) : Listener {

    // --- 몹 ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        if (!drops.ready) return
        val dead = event.entity
        if (dead is Player || !Mobs.include(dead.type)) return
        // 플레이어가 직접 처치한 것만(사용자 결정). 바닐라의 "플레이어 처치" 판정 그대로다.
        val killer = dead.killer ?: return
        if (!Drops.playable(killer) || isCustomMonster(dead)) return
        if (!drops.config.allows(dead.world.name, Category.MOB)) return
        val looting = killer.inventory.itemInMainHand.getEnchantmentLevel(Enchantment.LOOTING)
        roll(drops.tables.forMob(dead.type), dead.location, killer, looting, auto = false)
    }

    // --- 블록·작물(사람) ----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (!drops.ready) return
        val block = event.block
        // 놓은 블록인지는 기록을 지우기 **전에** 본다. 기록은 결과와 상관없이 지운다 — 블록이 사라진다.
        val placed = forget(block)
        firstThisTick(block)
        val player = event.player
        // isDropItems: 커스텀아이템이 커스텀 블록(소리블록·후렴초를 빌린 것)에서 꺼 둔다 — 그 블록은 자동으로 빠진다.
        if (placed || !Drops.playable(player) || !event.isDropItems) return
        val tool = player.inventory.itemInMainHand
        // 섬세한 손길이면 언제나 없다(사용자 결정) — 들고 가서 다시 놓는 반복의 시작을 끊는다.
        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) return
        val fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE)

        val crop = drops.crops.of(block.type)
        if (crop != null) {
            if (!drops.config.allows(block.world.name, Category.CROP) || !ready(crop, block)) return
            roll(drops.tables.forCrop(crop), center(block), player, fortune, auto = false)
            return
        }
        val table = drops.tables.block(block.type) ?: return
        if (!drops.config.allows(block.world.name, Category.BLOCK)) return
        // 맞는 도구가 필요한 블록을 맞지 않는 도구로 캐면 바닐라도 아무것도 안 준다. 유리처럼 도구가 필요 없는 블록은 맨손도 된다.
        val data = block.blockData
        if (data.requiresCorrectToolForDrops() && !data.isPreferredTool(tool)) return
        roll(listOf(table), center(block), player, fortune, auto = false)
    }

    /** 달콤한 열매·발광 열매를 우클릭으로 딴 것. 블록은 그대로라 기록과 상관없다. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHarvest(event: PlayerHarvestBlockEvent) {
        if (!drops.ready) return
        val block = event.harvestedBlock
        val crop = drops.crops.of(block.type)?.takeIf { it.rule == CropRule.BERRIES } ?: return
        val player = event.player
        if (!Drops.playable(player) || !drops.config.allows(block.world.name, Category.CROP)) return
        roll(drops.tables.forCrop(crop), center(block), player, 0, auto = false)
    }

    // --- 자동 농사 -----------------------------------------------------------------------------

    /** 피스톤·물이 부순 것. 취소할 수 없는 사건이다. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBreakBlock(event: BlockBreakBlockEvent) = auto(event.block)

    /** 받침이 무너짐·주민 수확 등. 떨구지 않는 파괴(`willDrop` false)는 기록만 지운다. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDestroy(event: BlockDestroyEvent) {
        if (event.willDrop()) auto(event.block) else forget(event.block)
    }

    private fun auto(block: Block) {
        if (!drops.ready) return
        val placed = forget(block)
        if (!firstThisTick(block) || placed) return
        val crop = drops.crops.of(block.type) ?: return
        if (!drops.config.allows(block.world.name, Category.CROP) || !ready(crop, block)) return
        val chunk = block.world.name + ":" + (block.x shr 4) + ":" + (block.z shr 4)
        val now = System.currentTimeMillis()
        if (!drops.autoCap.allows(chunk, now, drops.config.autoCap)) return
        val made = roll(drops.tables.forCrop(crop), center(block), null, 0, auto = true)
        drops.autoCap.add(chunk, now, made)
    }

    // --- 공통 ----------------------------------------------------------------------------------

    /** 표마다 따로 굴려 내준다. 나온 줄 수. */
    private fun roll(tables: List<DropTable>, location: Location, owner: Player?, level: Int, auto: Boolean): Int {
        val factor = drops.boost.factor()
        var made = 0
        for (table in tables) {
            if (!table.enabled || table.entries.isEmpty()) continue
            val rolled = Roller.roll(table.entries, { entry ->
                if (auto) Roller.chance(entry.autoChance, 0.0, 0, factor) else Roller.chance(entry.chance, table.bonus, level, factor)
            })
            drops.delivery.deliver(table, rolled, location, owner, auto)
            made += rolled.size
        }
        return made
    }

    /** 놓은 블록이었으면 기록을 지우고 true. 기록하는 블록이 아니면 PDC 를 읽지도 않는다. */
    private fun forget(block: Block): Boolean = drops.tables.isTracked(block.type) && drops.placed.unmark(block)

    private var tick = -1
    private val seen = HashSet<String>()

    /** 이번 틱에 이 자리를 처음 보는가. */
    private fun firstThisTick(block: Block): Boolean {
        val now = Bukkit.getCurrentTick()
        if (now != tick) {
            tick = now
            seen.clear()
        }
        return seen.add(block.world.name + ":" + block.x + ":" + block.y + ":" + block.z)
    }

    private fun center(block: Block): Location = block.location.add(0.5, 0.5, 0.5)

    companion object {

        /**
         * 몬스터 플러그인의 커스텀 몹 표식(`inmc-monster:mob_id`). **비어 있지 않으면** 커스텀 몹이다 — 자기 드랍 표가 있다.
         * 수식어만 붙은 바닐라 몹은 빈 값을 갖고 여전히 그 바닐라 몹이라 우리 표가 적용된다. 클래스에 기대지 않으려고 PDC 로 본다.
         */
        val MONSTER_ID = NamespacedKey("inmc-monster", "mob_id")

        fun isCustomMonster(entity: Entity): Boolean =
            entity.persistentDataContainer.get(MONSTER_ID, PersistentDataType.STRING)?.isNotEmpty() == true

        /** 지금 캐면 나오는 작물인가 — 블록에서 값을 꺼내 [CropRule.ready] 에 넘긴다. */
        fun ready(kind: CropKind, block: Block): Boolean {
            val data = block.blockData
            val ageable = data as? Ageable
            val berries = (data as? CaveVinesPlant)?.hasBerries()
            return kind.rule.ready(ageable?.age, ageable?.maximumAge, berries)
        }
    }
}
