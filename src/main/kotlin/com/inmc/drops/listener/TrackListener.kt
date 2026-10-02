package com.inmc.drops.listener

import com.inmc.drops.Drops
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.PistonMoveReaction
import org.bukkit.entity.Enderman
import org.bukkit.entity.FallingBlock
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataType

/**
 * 놓은 블록 기록을 블록을 따라 옮기고 지운다. 기록하는 블록([com.inmc.drops.table.TableStore.tracked])만 본다.
 *
 * - 놓음: 플레이어가 놓은 것, 엔더맨이 놓은 것
 * - 따라감: 피스톤이 민 것·당긴 것, 떨어지는 블록(모래·자갈 — 떨어지는 동안은 그 엔티티에)
 * - 지움: 폭발·불·잎 시듦·몹이 바꾼 것, **자라서 생긴 블록**(놓은 자리에 자연히 자랐다면 그것은 자연이다)
 *
 * 캐서 사라지는 것은 [DropListener] 가 지운다(지우기 전에 봐야 하므로).
 */
class TrackListener(private val drops: Drops) : Listener {

    private val fallingKey = NamespacedKey(Drops.NAMESPACE, "placed")

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        if (drops.tables.isTracked(block.type)) drops.placed.mark(block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onExtend(event: BlockPistonExtendEvent) = moved(event.blocks, event.direction)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRetract(event: BlockPistonRetractEvent) = moved(event.blocks, event.direction)

    /**
     * 피스톤의 블록 목록에는 **밀리는 것과 부서지는 것이 같이** 들어 있다. 밀리는 것([PistonMoveReaction.MOVE]·`PUSH_ONLY`)만 기록을
     * 옮긴다 — 부서지는 것은 [DropListener.onBreakBlock] 이 기록을 보고 지운다. 다 지운 뒤에 새 자리에 적는다(줄지어 밀리면 자리가 겹친다).
     */
    private fun moved(blocks: List<Block>, direction: BlockFace) {
        val moving = blocks.filter { block ->
            val reaction = block.pistonMoveReaction
            (reaction == PistonMoveReaction.MOVE || reaction == PistonMoveReaction.PUSH_ONLY) &&
                drops.tables.isTracked(block.type) && drops.placed.isPlaced(block)
        }
        for (block in moving) drops.placed.unmark(block)
        for (block in moving) drops.placed.mark(block.getRelative(direction))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChange(event: EntityChangeBlockEvent) {
        val block = event.block
        val entity = event.entity
        when {
            // 떨어지기 시작 — 기록을 엔티티로 옮긴다.
            entity is FallingBlock && event.to.isAir -> {
                if (drops.tables.isTracked(block.type) && drops.placed.unmark(block)) {
                    entity.persistentDataContainer.set(fallingKey, PersistentDataType.BYTE, 1)
                }
            }
            // 내려앉음 — 엔티티에 있던 기록을 새 자리에.
            entity is FallingBlock -> {
                if (entity.persistentDataContainer.has(fallingKey, PersistentDataType.BYTE)) drops.placed.mark(block)
            }
            entity is Enderman && !event.to.isAir -> {
                if (drops.tables.isTracked(event.to)) drops.placed.mark(block)
            }
            // 그 밖에 몹이 블록을 바꾸거나 없앴다(주민 수확·엔더맨이 집어 감 …).
            event.to != block.type -> {
                if (drops.tables.isTracked(block.type)) drops.placed.unmark(block)
            }
        }
    }

    /** 자라서 생긴 블록 — 그 자리의 낡은 기록을 지운다(사탕수수·수박·호박 …). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGrow(event: BlockGrowEvent) {
        if (drops.tables.isTracked(event.newState.type)) drops.placed.unmark(event.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSpread(event: BlockSpreadEvent) {
        if (drops.tables.isTracked(event.newState.type)) drops.placed.unmark(event.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = forget(event.blockList())

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = forget(event.blockList())

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) = forget(listOf(event.block))

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDecay(event: LeavesDecayEvent) = forget(listOf(event.block))

    private fun forget(blocks: List<Block>) {
        for (block in blocks) if (drops.tables.isTracked(block.type)) drops.placed.unmark(block)
    }
}
