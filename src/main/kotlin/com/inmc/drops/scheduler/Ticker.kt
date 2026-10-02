package com.inmc.drops.scheduler

import com.inmc.drops.Drops
import kr.inmc.core.scheduler.TickerBase

/** 1초마다 — 드랍 이벤트 보스바. 30초마다 — 표·통계 저장, 지난 자동 농사 창 버리기. */
class Ticker(private val drops: Drops) : TickerBase(drops.plugin) {

    override val periodTicks: Long = 20L

    private var count = 0

    override fun ready(): Boolean = drops.ready

    override fun tick(now: Long) {
        step("드랍 이벤트") { drops.boost.tick(now) }
        if (++count % SAVE_EVERY != 0) return
        step("표 저장") { drops.tables.flush() }
        step("통계 저장") { drops.stats.flush() }
        step("자동 농사 창") { drops.autoCap.prune(now) }
    }

    private companion object {
        const val SAVE_EVERY = 30
    }
}
