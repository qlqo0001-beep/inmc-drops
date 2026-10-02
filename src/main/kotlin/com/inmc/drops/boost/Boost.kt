package com.inmc.drops.boost

import com.inmc.drops.Drops
import com.inmc.drops.util.Ph
import kr.inmc.core.store.YamlFileStore
import kr.inmc.core.util.Durations
import kr.inmc.core.util.Numbers
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

/**
 * 드랍 이벤트 — 잠깐 모든 커스텀 드랍 확률에 [multiplier] 를 곱한다(사람·자동 농사 둘 다. 자동 농사는 청크 상한이 그대로 막는다).
 *
 * `state.yml` 에 적어 재시작해도 이어진다. 끝나는 시각은 키가 있을 때만 적는다 — 0 을 "없음"으로 쓰지 않는다(지뢰 4).
 * 남은 시간은 보스바로 모두에게(새로 들어온 사람에게도 — [show]).
 */
class Boost(private val drops: Drops) : YamlFileStore(drops.io, listOf("state.yml"), HEADER, "드랍 이벤트") {

    var multiplier: Double = 1.0
        private set

    private var startedAt: Long? = null

    var endsAt: Long? = null
        private set

    private var bar: BossBar? = null

    /** 검증기가 도는 동안 배율을 1 로 — 1 보다 작은 이벤트가 걸려 있으면 100% 항목도 흔들린다. */
    @Volatile
    var suspended: Boolean = false

    fun active(now: Long = System.currentTimeMillis()): Boolean = endsAt?.let { now < it } == true

    /** 확률에 곱할 값. 이벤트가 없으면 1. */
    fun factor(now: Long = System.currentTimeMillis()): Double = if (!suspended && active(now)) multiplier else 1.0

    fun start(multiplier: Double, minutes: Long, now: Long = System.currentTimeMillis()) {
        this.multiplier = multiplier.coerceIn(MIN, MAX)
        startedAt = now
        endsAt = now + minutes.coerceIn(1, MAX_MINUTES) * 60_000L
        save()
        refreshBar(now)
        for (player in Bukkit.getOnlinePlayers()) show(player)
    }

    /** 시작하고 모두에게 알린다 — 명령어와 화면이 같이 쓴다. */
    fun startAnnounced(multiplier: Double, minutes: Long) {
        start(multiplier, minutes)
        val ph = Ph.of().amount(Numbers.chance(this.multiplier)).value(Durations.format(minutes.coerceIn(1, MAX_MINUTES) * 60))
        for (player in Bukkit.getOnlinePlayers()) drops.messages.send(player, "boost-started", ph)
    }

    /** 끝낸다. 진행 중이 아니었으면 false. */
    fun stop(): Boolean {
        if (endsAt == null) return false
        multiplier = 1.0
        startedAt = null
        endsAt = null
        save()
        hideAll()
        return true
    }

    /** 보스바를 내린다 — 끝날 때, 그리고 플러그인이 꺼질 때(안 내리면 화면에 남는다). */
    fun hideAll() {
        bar?.let { b -> for (player in Bukkit.getOnlinePlayers()) player.hideBossBar(b) }
        bar = null
    }

    /** 틱커가 1초마다. 시간이 다 됐으면 끝내고 알린다. */
    fun tick(now: Long) {
        val end = endsAt ?: return
        if (now >= end) {
            stop()
            for (player in Bukkit.getOnlinePlayers()) drops.messages.send(player, "boost-ended")
            return
        }
        refreshBar(now)
    }

    fun show(player: Player) {
        if (!active()) return
        refreshBar(System.currentTimeMillis())
        bar?.let(player::showBossBar)
    }

    fun remainingText(now: Long = System.currentTimeMillis()): String =
        Durations.format(((endsAt ?: now) - now).coerceAtLeast(0) / 1000)

    private fun refreshBar(now: Long) {
        val end = endsAt ?: return
        val start = startedAt ?: now
        val total = (end - start).coerceAtLeast(1)
        val progress = ((end - now).toDouble() / total).coerceIn(0.0, 1.0).toFloat()
        val title = drops.messages.component("boost-bar", Ph.of().amount(Numbers.chance(multiplier)).value(remainingText(now)))
        val current = bar
        if (current == null) {
            bar = BossBar.bossBar(title, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS)
        } else {
            current.name(title)
            current.progress(progress)
        }
    }

    private fun save() {
        markDirty()
        flush()
    }

    override fun read(config: YamlConfiguration) {
        multiplier = config.getDouble("event.multiplier", 1.0).coerceIn(MIN, MAX)
        startedAt = if (config.contains("event.started-at")) config.getLong("event.started-at") else null
        endsAt = if (config.contains("event.ends-at")) config.getLong("event.ends-at") else null
    }

    override fun write(config: YamlConfiguration) {
        val end = endsAt ?: return
        config.set("event.multiplier", multiplier)
        startedAt?.let { config.set("event.started-at", it) }
        config.set("event.ends-at", end)
    }

    companion object {
        const val MIN = 0.01
        const val MAX = 100.0
        /** 일주일. 그보다 길면 "이벤트"가 아니라 확률을 고칠 일이다. */
        const val MAX_MINUTES = 7L * 24L * 60L
        private const val HEADER = "inmc-drops 진행 중인 드랍 이벤트. /드랍 배율 로 바꾼다 — 손으로 고칠 일은 없다."
    }
}
