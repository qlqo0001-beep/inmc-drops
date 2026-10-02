package com.inmc.drops.verify

import com.inmc.drops.Drops
import com.inmc.drops.catalog.Mobs
import com.inmc.drops.listener.DropListener
import com.inmc.drops.roll.Delivery
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropEntry
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Ph
import io.papermc.paper.event.block.BlockBreakBlockEvent
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.Ageable
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Cow
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerHarvestBlockEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * `/드랍 검증` — 서버 안에서 **진짜 사건을 쏘아** 판정을 확인한다(커스텀아이템·상점 검증기와 같은 방식). 서버 없이 도는 단위 시험이
 * 못 보는 것 — 리스너 배선, 블록 상태, 청크 기록, 다른 플러그인과의 순서 — 이 대상이다.
 *
 * - 검증하는 사람 머리 위 4칸 높이, 동쪽으로 [SPOTS]칸의 블록을 잠깐 바꿨다가 되돌린다(물리 계산 없이). 그 줄이 비어 있을 때만 돈다
 *   — 상자 같은 블록을 바꿨다 되돌리면 안의 것이 다칠 수 있다. 소는 잠깐 불렀다가 지운다.
 * - 표에는 100% 짜리 임시 항목(`zz_verify`)을 걸었다가 뺀다. 없던 표는 메모리에만 만들고 버린다 — 한 틱 안에 끝나 저장 틱커가 쓰지 않는다.
 * - 드랍은 **떨구지 않고 적기만** 한다([Delivery.capture]) — 아이템·명령어·공지·통계·신호가 나가지 않는다.
 * - 사건은 진짜라 다른 플러그인도 받는다(블록 기록 플러그인의 기록, 처치 수 …). 보호 구역에서 돌리면 취소돼 실패로 보인다.
 */
class Verifier(private val drops: Drops) {

    data class Result(val name: String, val failure: String?)

    private class Check(val name: String, val run: (Stage) -> String?)

    fun run(player: Player) {
        val stage = Stage(drops, player)
        // 블록을 잠깐 바꿨다가 되돌린다 — 상자 같은 것이 있으면 안의 것이 다칠 수 있어 빈 곳에서만 돈다.
        stage.blocked()?.let { reason ->
            drops.messages.send(player, "verify-failure", Ph.of().item("준비").value(reason))
            return
        }
        val results = try {
            stage.setUp()
            CHECKS.map { check ->
                val failure = try {
                    stage.reset()
                    check.run(stage)
                } catch (t: Throwable) {
                    "검증기 오류: " + t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
                }
                Result(check.name, failure)
            }
        } finally {
            stage.tearDown()
        }

        val failures = results.filter { it.failure != null }
        drops.messages.send(player, "verify-done", Ph.of().amount((results.size - failures.size).toString()).count(failures.size))
        for (failure in failures) drops.messages.send(player, "verify-failure", Ph.of().item(failure.name).value(failure.failure.orEmpty()))

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = drops.io.file("verify", "drops-$stamp.txt")
        val text = buildString {
            appendLine("# inmc-drops 검증 - ${LocalDateTime.now()}")
            for (row in results) appendLine((if (row.failure == null) "- 통과 " else "- 실패 ") + row.name + (row.failure?.let { " — $it" } ?: ""))
        }
        drops.io.asyncRun {
            file.parentFile.mkdirs()
            file.writeText(text)
        }
        drops.messages.send(player, "verify-report", Ph.of().value("plugins/${drops.plugin.name}/verify/${file.name}"))
    }

    /** 검사들이 쓰는 무대 — 임시 항목·블록 자리·사람의 원래 상태. */
    class Stage(val drops: Drops, val player: Player) {

        val captured = ArrayList<Delivery.Captured>()

        private val base: Block = player.location.block.getRelative(BlockFace.UP, 4)
        private val states = LinkedHashMap<Block, BlockState>()
        /** 손댄 표, 원래 있던 표인가, 원래 켜져 있었나. */
        private val touched = ArrayList<Triple<DropTable, Boolean, Boolean>>()
        private val hand = player.inventory.itemInMainHand.clone()
        private val gameMode = player.gameMode
        private val config = drops.config

        fun setUp() {
            player.gameMode = GameMode.SURVIVAL
            drops.delivery.capture = captured
            drops.boost.suspended = true
            val existing = drops.tables.block(Material.STONE)
            attach(existing ?: DropTable(Category.BLOCK, "zz_verify_stone", listOf("stone")), existing != null)
            for (crop in listOf("wheat", "sugar_cane", "sweet_berries")) attach(Category.CROP, crop)
            attach(Category.MOB, Mobs.id(org.bukkit.entity.EntityType.COW))
        }

        private fun attach(category: Category, id: String) {
            val existing = drops.tables.get(category, id)
            attach(existing ?: DropTable(category, id), existing != null)
        }

        /** 100% 항목을 단다. 없던 표는 메모리에만. */
        private fun attach(table: DropTable, existed: Boolean) {
            table.entries += DropEntry(id = VERIFY_ID, item = drops.resolver.capture(ItemStack(Material.PAPER)), chance = 100.0, autoChance = 100.0)
            if (!existed) drops.tables.putTemporary(table)
            touched += Triple(table, existed, table.enabled)
            // 관리자가 꺼 둔 표여도 검사는 돌아야 한다 — 끝나면 되돌린다.
            table.enabled = true
        }

        /** 검사마다 — 적은 것을 비우고, 앞 검사가 바꾼 설정을 되돌린다. */
        fun reset() {
            captured.clear()
            drops.config = config
        }

        fun tearDown() {
            drops.delivery.capture = null
            drops.boost.suspended = false
            for ((table, existed, enabled) in touched) {
                table.entries.removeIf { it.id == VERIFY_ID }
                table.enabled = enabled
                if (!existed) drops.tables.discard(table)
            }
            for ((block, state) in states) {
                drops.placed.unmark(block)
                state.update(true, false)
            }
            drops.config = config
            player.inventory.setItemInMainHand(hand)
            player.gameMode = gameMode
        }

        /** 검사할 줄(머리 위 4칸, 동쪽으로 [SPOTS]칸)이 다 비어 있지 않으면 그 까닭. */
        fun blocked(): String? {
            val world = base.world
            if (base.y + 1 >= world.maxHeight) return "머리 위 4칸이 월드 높이 밖입니다. 더 낮은 곳에서 돌리세요."
            val solid = (0 until SPOTS).map { base.getRelative(it, 0, 0) }.firstOrNull { !it.type.isAir }
            return solid?.let { "머리 위 4칸, 동쪽으로 ${SPOTS}칸이 비어 있어야 합니다(${it.x}, ${it.y}, ${it.z} 에 ${it.type.key.key}). 탁 트인 곳에서 돌리세요." }
        }

        /** [index] 번째 자리. 처음 쓰면 원래 상태를 적어 둔다. */
        fun spot(index: Int): Block {
            require(index in 0 until SPOTS) { "검사 자리 $index 는 비어 있는지 보지 않은 자리입니다 — SPOTS 를 늘리세요" }
            val block = base.getRelative(index, 0, 0)
            states.getOrPut(block) { block.state }
            drops.placed.unmark(block)
            return block
        }

        fun set(block: Block, material: Material, age: Int? = null) {
            val data = material.createBlockData()
            if (data is Ageable) data.age = if (age == null) data.maximumAge else age.coerceIn(0, data.maximumAge)
            block.setBlockData(data, false)
        }

        fun hold(stack: ItemStack?) = player.inventory.setItemInMainHand(stack)

        /** 플레이어가 캔 사건. 다른 플러그인이 취소했으면 그 이유를 실패로. */
        fun breakBlock(block: Block, preCancelled: Boolean = false): String? {
            val event = BlockBreakEvent(block, player)
            if (preCancelled) event.isCancelled = true
            event.callEvent()
            return if (!preCancelled && event.isCancelled) "다른 플러그인이 파괴를 취소했습니다(보호 구역이면 다른 곳에서)" else null
        }

        fun place(block: Block, material: Material): String? {
            val before = block.state
            set(block, material)
            val event = BlockPlaceEvent(block, before, block.getRelative(BlockFace.DOWN), ItemStack(material), player, true, EquipmentSlot.HAND)
            event.callEvent()
            return if (event.isCancelled) "다른 플러그인이 설치를 취소했습니다(보호 구역이면 다른 곳에서)" else null
        }

        fun ours(): List<Delivery.Captured> = captured.filter { it.entryId == VERIFY_ID }

        /** 우리 항목이 나왔어야 한다. */
        fun expectDrop(auto: Boolean = false): String? {
            val hit = ours()
            return when {
                hit.isEmpty() -> "나와야 하는데 안 나왔습니다"
                hit.any { it.auto != auto } -> if (auto) "자동 농사로 나와야 하는데 사람 확률로 나왔습니다" else "사람 확률로 나와야 하는데 자동 농사로 나왔습니다"
                !auto && hit.any { it.owner != player.uniqueId } -> "주인이 캔 사람이 아닙니다"
                auto && hit.any { it.owner != null } -> "자동 농사 드랍에 주인이 있습니다"
                else -> null
            }
        }

        fun expectNone(): String? = if (ours().isEmpty()) null else "나오면 안 되는데 나왔습니다"

        fun cow(): Cow = player.world.spawn(player.location.add(0.0, 0.0, 2.0), Cow::class.java) {
            it.setAI(false)
            it.isSilent = true
            it.isPersistent = false
        }

        fun death(cow: Cow, killer: Player?): EntityDeathEvent {
            val source = DamageSource.builder(if (killer != null) DamageType.PLAYER_ATTACK else DamageType.GENERIC)
            if (killer != null) source.withCausingEntity(killer).withDirectEntity(killer)
            cow.killer = killer
            return EntityDeathEvent(cow, source.build(), mutableListOf())
        }
    }

    companion object {

        const val VERIFY_ID = "zz_verify"

        /** 검사가 쓰는 자리 수(spot 0 ~ 16). 검사를 더하면 늘린다. */
        const val SPOTS = 17

        private fun pickaxe(silk: Boolean = false) = ItemStack(Material.DIAMOND_PICKAXE).also { if (silk) it.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1) }

        private val CHECKS: List<Check> = listOf(
            Check("자연 블록을 캐면 나온다") { s ->
                val block = s.spot(0)
                s.set(block, Material.STONE)
                s.hold(pickaxe())
                s.breakBlock(block) ?: s.expectDrop()
            },
            Check("놓은 블록을 캐면 안 나온다") { s ->
                val block = s.spot(1)
                s.hold(pickaxe())
                s.place(block, Material.STONE)
                    ?: (if (!s.drops.placed.isPlaced(block)) "놓은 블록이 기록되지 않았습니다" else null)
                    ?: s.breakBlock(block) ?: s.expectNone()
                    ?: (if (s.drops.placed.isPlaced(block)) "캔 뒤에도 기록이 남았습니다" else null)
            },
            Check("섬세한 손길이면 안 나온다") { s ->
                val block = s.spot(2)
                s.set(block, Material.STONE)
                s.hold(pickaxe(silk = true))
                s.breakBlock(block) ?: s.expectNone()
            },
            Check("맞지 않는 도구(돌을 맨손)면 안 나온다") { s ->
                val block = s.spot(3)
                s.set(block, Material.STONE)
                s.hold(null)
                s.breakBlock(block) ?: s.expectNone()
            },
            Check("보호 플러그인이 취소한 파괴는 안 나온다") { s ->
                val block = s.spot(4)
                s.set(block, Material.STONE)
                s.hold(pickaxe())
                s.breakBlock(block, preCancelled = true) ?: s.expectNone()
            },
            Check("덜 자란 밀은 안 나온다") { s ->
                val block = s.spot(5)
                s.set(block, Material.WHEAT, age = 0)
                s.hold(null)
                s.breakBlock(block) ?: s.expectNone()
            },
            Check("다 자란 밀은 나온다") { s ->
                val block = s.spot(6)
                s.set(block, Material.WHEAT)
                s.hold(null)
                s.breakBlock(block) ?: s.expectDrop()
            },
            Check("자라난 사탕수수는 나온다") { s ->
                val block = s.spot(7)
                s.set(block, Material.SUGAR_CANE, age = 0)
                s.hold(null)
                s.breakBlock(block) ?: s.expectDrop()
            },
            Check("놓은 사탕수수는 안 나온다") { s ->
                val block = s.spot(8)
                s.hold(null)
                s.place(block, Material.SUGAR_CANE) ?: s.breakBlock(block) ?: s.expectNone()
            },
            Check("피스톤이 민 놓은 블록은 기록이 따라간다") { s ->
                val block = s.spot(9)
                val next = s.spot(10)
                s.set(block, Material.STONE)
                s.drops.placed.mark(block)
                BlockPistonExtendEvent(s.spot(11), listOf(block), BlockFace.EAST).callEvent()
                when {
                    s.drops.placed.isPlaced(block) -> "원래 자리에 기록이 남았습니다"
                    !s.drops.placed.isPlaced(next) -> "밀려난 자리에 기록이 없습니다"
                    else -> null
                }
            },
            Check("피스톤·물로 캔 다 자란 밀은 자동 농사 확률로 나온다") { s ->
                val block = s.spot(12)
                s.set(block, Material.WHEAT)
                BlockBreakBlockEvent(block, s.spot(13), mutableListOf()).callEvent()
                s.expectDrop(auto = true)
            },
            Check("같은 틱·같은 자리의 두 번째 파괴는 세지 않는다") { s ->
                val block = s.spot(12)
                BlockBreakBlockEvent(block, s.spot(13), mutableListOf()).callEvent()
                s.expectNone()
            },
            Check("자동 농사 상한이 0 이면 안 나온다") { s ->
                val block = s.spot(14)
                s.set(block, Material.WHEAT)
                s.drops.config = s.drops.config.copy(autoCap = 0)
                BlockBreakBlockEvent(block, s.spot(13), mutableListOf()).callEvent()
                s.expectNone()
            },
            Check("꺼진 월드에서는 안 나온다") { s ->
                val block = s.spot(15)
                s.set(block, Material.STONE)
                s.hold(pickaxe())
                val world = block.world.name
                s.drops.config = s.drops.config.withWorld(world, s.drops.config.world(world).with(Category.BLOCK, false))
                s.breakBlock(block) ?: s.expectNone()
            },
            Check("열매를 우클릭으로 따면 나온다") { s ->
                val block = s.spot(16)
                s.set(block, Material.SWEET_BERRY_BUSH)
                PlayerHarvestBlockEvent(s.player, block, EquipmentSlot.HAND, mutableListOf()).callEvent()
                s.expectDrop()
            },
            Check("플레이어가 잡은 몹은 나온다") { s ->
                val cow = s.cow()
                try {
                    s.hold(null)
                    s.death(cow, s.player).callEvent()
                    s.expectDrop()
                } finally {
                    cow.remove()
                }
            },
            Check("플레이어가 잡지 않은 몹은 안 나온다") { s ->
                val cow = s.cow()
                try {
                    s.death(cow, null).callEvent()
                    s.expectNone()
                } finally {
                    cow.remove()
                }
            },
            Check("커스텀 몹(몬스터 플러그인 표식)은 안 나온다") { s ->
                val cow = s.cow()
                try {
                    cow.persistentDataContainer.set(DropListener.MONSTER_ID, PersistentDataType.STRING, "zz_verify")
                    s.death(cow, s.player).callEvent()
                    s.expectNone()
                } finally {
                    cow.remove()
                }
            },
            Check("줍기 보호는 주인만 통과시킨다") { s ->
                val item = s.player.world.dropItem(s.player.location.add(0.0, 1.0, 0.0), ItemStack(Material.PAPER))
                try {
                    s.drops.delivery.protect(item, s.player.uniqueId, 5)
                    when {
                        !s.drops.delivery.canPickUp(item, s.player.uniqueId) -> "주인이 못 줍습니다"
                        s.drops.delivery.canPickUp(item, UUID.randomUUID()) -> "다른 사람이 주울 수 있습니다"
                        else -> null
                    }
                } finally {
                    item.remove()
                }
            },
        )
    }
}
