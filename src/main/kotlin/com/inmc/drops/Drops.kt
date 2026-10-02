package com.inmc.drops

import com.inmc.drops.boost.Boost
import com.inmc.drops.catalog.Crops
import com.inmc.drops.config.DropsConfig
import com.inmc.drops.config.Messages
import com.inmc.drops.roll.AutoCap
import com.inmc.drops.roll.Delivery
import com.inmc.drops.stats.DropStats
import com.inmc.drops.table.TableStore
import com.inmc.drops.track.PlacedBlocks
import com.inmc.drops.util.Ph
import kr.inmc.core.InmcHost
import kr.inmc.core.config.ConfigService
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.integration.MMOItemsHook
import kr.inmc.core.item.ItemResolver
import kr.inmc.core.util.Placeholders
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * 플러그인을 엮는 서비스 로케이터.
 *
 * 다른 inmc 플러그인을 모른다 — 아이템은 core [ItemResolver](커스텀아이템·MMOItems 참조), 업적은 core 신호로만 만난다.
 */
class Drops(override val plugin: JavaPlugin) : InmcHost {

    val logger: java.util.logging.Logger = plugin.logger

    override val io = ConfigService(plugin)

    override fun tell(target: CommandSender, key: String, ph: Placeholders?) = messages.send(target, key, ph as? Ph)

    @Volatile
    var config: DropsConfig = DropsConfig()

    @Volatile
    var messages: Messages = Messages.from(YamlConfiguration())

    val customItems = CustomItemHook(logger)

    val mmoItems = MMOItemsHook(logger)

    /** 등록한 아이템을 만들고 알아본다(바닐라·커스텀아이템·MMOItems). */
    val resolver = ItemResolver(mmoItems, customItems, logger)

    /** [tables] 보다 먼저 — 표를 다시 짤 때 작물 목록도 같이 짠다. */
    val crops = Crops()

    val tables = TableStore(this)

    val stats = DropStats(io)

    val boost = Boost(this)

    val placed = PlacedBlocks()

    val autoCap = AutoCap()

    val delivery = Delivery(this)

    @Volatile
    var ready = false
        private set

    fun markReady() {
        ready = true
    }

    /** 화면에서 고친 설정을 메모리와 파일에. */
    fun updateConfig(change: (DropsConfig) -> DropsConfig) {
        config = change(config)
        val yaml = config.toYaml()
        io.asyncRun { io.save(io.file("config.yml"), yaml) }
    }

    /** 리로드 때 — 버려질 표를 계속 고치지 않게 열린 우리 화면을 닫는다. */
    fun closeMenus() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.holder
            if (holder is kr.inmc.core.gui.Menu && holder.owner === this) player.closeInventory()
        }
    }

    companion object {

        /** PDC 네임스페이스 — 플러그인 이름이 바뀌어도 이미 찍힌 기록·아이템이 정체를 잃지 않게 상수로 고정한다. */
        const val NAMESPACE = "inmcdrops"

        const val ADMIN = "inmcdrops.admin"
        const val INFO = "inmcdrops.info"

        /** 서바이벌·모험만 — 크리에이티브로 부수거나 잡아서 뽑지 못하게. */
        fun playable(player: Player): Boolean = player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE
    }
}
