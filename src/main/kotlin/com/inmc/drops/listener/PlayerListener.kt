package com.inmc.drops.listener

import com.inmc.drops.Drops
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerJoinEvent

/** 줍기 보호와, 들어온 사람에게 진행 중인 드랍 이벤트 보스바. */
class PlayerListener(private val drops: Drops) : Listener {

    /** LOW — 커스텀아이템의 배낭 자동 수납(HIGH, `ignoreCancelled`)보다 먼저. 몬스터 플러그인과 같은 자리다. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        if (!drops.delivery.canPickUp(event.item, event.player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = drops.boost.show(event.player)
}
