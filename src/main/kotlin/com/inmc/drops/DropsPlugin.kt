package com.inmc.drops

import com.inmc.drops.command.DropsCommand
import com.inmc.drops.config.DropsConfig
import com.inmc.drops.config.Messages
import com.inmc.drops.listener.DropListener
import com.inmc.drops.listener.PlayerListener
import com.inmc.drops.listener.TrackListener
import com.inmc.drops.roll.Delivery
import com.inmc.drops.scheduler.Ticker
import com.inmc.drops.table.Category
import kr.inmc.core.event.SignalCatalog
import org.bukkit.plugin.java.JavaPlugin

/**
 * 켜질 때 표를 **그 자리에서** 읽는다 — 첫 블록을 캐기 전에 표가 있어야 한다. 서버가 틱을 돌기 전이라 괜찮다.
 */
class DropsPlugin : JavaPlugin() {

    private lateinit var drops: Drops
    private lateinit var ticker: Ticker

    override fun onEnable() {
        drops = Drops(this)
        for (name in RESOURCES) drops.io.copyDefault(name, drops.io.file(name))
        drops.config = DropsConfig.from(drops.io.load(drops.io.file("config.yml")))
        drops.messages = Messages.from(drops.io.load(drops.io.file("messages.yml")))
        drops.mmoItems.setup()
        drops.customItems.setup()
        drops.tables.loadNow()
        drops.stats.load()
        drops.boost.load { for (player in server.onlinePlayers) drops.boost.show(player) }

        val manager = server.pluginManager
        manager.registerEvents(DropListener(drops), this)
        manager.registerEvents(TrackListener(drops), this)
        manager.registerEvents(PlayerListener(drops), this)
        manager.registerEvents(kr.inmc.core.listener.MenuListener(drops), this)
        DropsCommand(drops, this).register(this)

        // 업적이 "커스텀 드랍을 n번" 을 셀 수 있게. 고를 수 있는 대상은 표에 들어 있는 아이템.
        SignalCatalog.register(
            Delivery.SIGNAL_SOURCE, Delivery.SIGNAL_TYPE, "드랍 아이템",
            subjects = {
                Category.entries.flatMap { drops.tables.all(it) }.flatMap { it.entries }
                    .map { it.item.ref.serialize() to it.label() }.distinctBy { it.first }
            },
            dataKeys = listOf("category" to "종류(mob·crop·block·group)", "table" to "표 id", "auto" to "자동 농사(true·false)"),
            description = "커스텀 드랍이 나왔을 때(사람이 만든 것만 — 자동 농사는 주인이 없다). 개수 = 나온 개수",
        )

        ticker = Ticker(drops)
        drops.markReady()
        ticker.start()
        logger.info("inmc-drops 활성화 완료 - 설정된 표 ${drops.tables.configuredCount()}개")
    }

    /**
     * 연동용 안정 진입점 — inmc-monster 가 드랍 이벤트 배율을 리플렉션으로 읽는다.
     * 시그니처를 바꾸면 양쪽 CHANGELOG 에 적는다. 없거나 꺼져 있으면 1.0.
     */
    fun boostFactor(): Double =
        if (::drops.isInitialized) drops.boost.factor() else 1.0

    override fun onDisable() {
        if (!::drops.isInitialized) return
        if (::ticker.isInitialized) ticker.stop()
        SignalCatalog.unregisterAll(Delivery.SIGNAL_SOURCE)
        drops.boost.hideAll()
        drops.tables.flushBlocking()
        drops.stats.flushBlocking()
        drops.boost.flushBlocking()
        drops.io.shutdown()
    }

    /** `/드랍 리로드`. 고친 표를 먼저 쓰고, 파일은 워커에서 읽고 반영은 메인에서. */
    fun reload(then: () -> Unit) {
        drops.closeMenus()
        drops.stats.flushBlocking()
        drops.io.async({ drops.io.load(drops.io.file("config.yml")) to drops.io.load(drops.io.file("messages.yml")) }) { (config, messages) ->
            drops.config = DropsConfig.from(config)
            drops.messages = Messages.from(messages)
            drops.tables.reload(then)
        }
    }

    private companion object {
        val RESOURCES = listOf("config.yml", "messages.yml")
    }
}
