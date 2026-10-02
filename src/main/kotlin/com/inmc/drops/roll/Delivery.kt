package com.inmc.drops.roll

import com.inmc.drops.Drops
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import com.inmc.drops.util.Ph
import kr.inmc.core.event.InmcSignalEvent
import kr.inmc.core.integration.PlayerSettings
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * 굴려서 나온 것을 내준다 — 바닥에 떨구고, 명령어를 돌리고, 공지하고, 세고, 업적에 알린다.
 *
 * **이벤트의 드랍 목록에 넣지 않고 따로 떨군다.** 인첸트가 HIGH 에서 그 목록을 고친다(배수·제련·가방으로 —
 * `inmc-enchants/…/listener/ActionListener.kt` `onDrops`). 목록에 넣으면 배수 인첸트가 커스텀 드랍까지 불린다.
 *
 * 줍기 보호는 몬스터 플러그인(`death/DropService.kt`)과 같은 방식이다 — 주인과 만료 시각을 **아이템 엔티티에** 적고, 줍는 사건에서
 * 비교한다([canPickUp]). 청크가 내려가도 남고 지우는 틱이 필요 없다. 커스텀아이템의 배낭 자동 수납(HIGH, `ignoreCancelled`)은
 * 우리가 LOW 에서 취소한 것을 건드리지 않는다.
 */
class Delivery(private val drops: Drops) {

    private val ownerKey = NamespacedKey(Drops.NAMESPACE, "owner")
    private val untilKey = NamespacedKey(Drops.NAMESPACE, "owner_until")

    /** 검증기가 켜면 떨구지 않고 여기에 적는다(명령어·공지·통계·신호도 건너뛴다). */
    var capture: MutableList<Captured>? = null

    data class Captured(val table: DropTable, val entryId: String, val amount: Int, val auto: Boolean, val owner: UUID?)

    /**
     * @param owner 드랍을 만든 사람. 자동 농사는 null — 주인 없는 드랍이고 명령어·공지·신호가 없다.
     */
    fun deliver(table: DropTable, rolled: List<Rolled>, location: Location, owner: Player?, auto: Boolean) {
        if (rolled.isEmpty()) return
        capture?.let { list ->
            for (r in rolled) list += Captured(table, r.entry.id, r.amount, auto, owner?.uniqueId)
            return
        }
        for (r in rolled) {
            val entry = r.entry
            var first: ItemStack? = null
            if (entry.giveItem) {
                var left = r.amount
                while (left > 0) {
                    val stack = drops.resolver.create(entry.item, left.coerceAtMost(MAX_PER_CREATE)) ?: break
                    val made = stack.amount
                    if (made <= 0) break
                    if (first == null) first = stack.clone()
                    left -= made
                    for (part in split(stack)) dropOnGround(location, part, owner?.uniqueId)
                }
            }
            drops.stats.add(table.category, table.id, entry.id)
            if (owner == null) continue
            for (command in entry.commands) runCommand(command, owner)
            val label = first?.let(Labels::of) ?: entry.label()
            if (entry.announce) {
                val ph = Ph.of().player(owner.name).item(label).source(Labels.table(drops, table))
                // 개인 설정 "희귀 드랍·당첨 공지 받기"를 끈 사람은 빼고 — 얻은 본인은 늘 본다(core PlayerSettings).
                for (player in Bukkit.getOnlinePlayers()) {
                    if (player != owner && !PlayerSettings.enabled(player, PlayerSettings.RARE_ANNOUNCE)) continue
                    drops.messages.send(player, "announce", ph)
                }
            }
            InmcSignalEvent.fire(SIGNAL_SOURCE, SIGNAL_TYPE, owner.uniqueId, entry.item.ref.serialize(), r.amount.toLong(), owner) {
                mapOf("category" to table.category.id, "table" to table.id, "auto" to auto.toString())
            }
        }
    }

    /** 겹침 수(16·1 인 아이템)를 넘는 덩어리를 나눈다. */
    private fun split(stack: ItemStack): List<ItemStack> {
        val max = stack.maxStackSize.coerceAtLeast(1)
        if (stack.amount <= max) return listOf(stack)
        val parts = ArrayList<ItemStack>()
        var left = stack.amount
        while (left > 0) {
            val n = left.coerceAtMost(max)
            parts += stack.clone().also { it.amount = n }
            left -= n
        }
        return parts
    }

    private fun dropOnGround(location: Location, stack: ItemStack, owner: UUID?) {
        val world = location.world ?: return
        val item = world.dropItemNaturally(location, stack)
        val seconds = drops.config.protectSeconds
        if (owner != null && seconds > 0) protect(item, owner, seconds)
    }

    fun protect(item: Item, owner: UUID, seconds: Int) {
        item.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, owner.toString())
        item.persistentDataContainer.set(untilKey, PersistentDataType.LONG, System.currentTimeMillis() + seconds * 1000L)
    }

    /** 지금 [player] 가 이것을 주울 수 있는가. 우리가 맡아 두지 않은 아이템(서버의 거의 전부)은 true. */
    fun canPickUp(item: Item, player: UUID): Boolean {
        val container = item.persistentDataContainer
        val owner = container.get(ownerKey, PersistentDataType.STRING) ?: return true
        val until = container.get(untilKey, PersistentDataType.LONG) ?: return true
        if (System.currentTimeMillis() >= until) {
            // 맡아 둔 시간이 지났다 — 표식을 지워 다음부터는 바로 통과하게.
            container.remove(ownerKey)
            container.remove(untilKey)
            return true
        }
        return owner == player.toString()
    }

    /** 명령어는 MiniMessage 를 거치지 않는다 — 꺾쇠와 `&` 가 사라진다. 자리표시만 바꾼다(core `RewardService.runCommand` 와 같은 이유). */
    private fun runCommand(template: String, player: Player) {
        val resolved = Text.substituteOnly(template, Ph.of().player(player.name), player).trim().removePrefix("/")
        if (resolved.isEmpty()) return
        runCatching { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved) }
            .onFailure { drops.logger.warning("드랍 명령어 실행 실패 ($resolved): ${it.message}") }
    }

    companion object {
        const val SIGNAL_SOURCE = "drops"
        const val SIGNAL_TYPE = "drop"

        /** 한 번에 만들 개수 — 항목의 최대 개수와 같다. */
        private const val MAX_PER_CREATE = com.inmc.drops.table.DropEntry.MAX_AMOUNT
    }
}
