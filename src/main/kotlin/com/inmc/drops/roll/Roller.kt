package com.inmc.drops.roll

import com.inmc.drops.table.DropEntry
import kotlin.math.ceil
import kotlin.random.Random

/** 굴려서 나온 줄과 개수. */
data class Rolled(val entry: DropEntry, val amount: Int)

/** 확률 계산과 굴림 — 서버 없이 도는 순수 계산(`RollerTest`). */
object Roller {

    /**
     * 최종 확률(%) = 기본 × (1 + 보너스% × 레벨) × 이벤트 배율. 0~100 으로 자른다 — 배율 3 이 이미 흔한 항목을 100% 넘게 만들지 않게.
     *
     * @param bonusPercent 표의 행운·약탈 보너스(레벨당 %)
     * @param level 도구의 행운·약탈 레벨(자동 농사는 0)
     * @param multiplier 드랍 이벤트 배율(없으면 1)
     */
    fun chance(base: Double, bonusPercent: Double, level: Int, multiplier: Double): Double {
        if (base <= 0.0) return 0.0
        val boosted = base * (1.0 + bonusPercent / 100.0 * level.coerceAtLeast(0)) * multiplier.coerceAtLeast(0.0)
        return boosted.coerceIn(0.0, 100.0)
    }

    /** 항목마다 따로 굴린다. [chanceOf] 가 0 이하인 줄과 아무것도 안 하는 줄은 굴리지 않는다. */
    fun roll(entries: List<DropEntry>, chanceOf: (DropEntry) -> Double, rng: Random = Random.Default): List<Rolled> =
        entries.mapNotNull { entry ->
            if (entry.isEmpty()) return@mapNotNull null
            val chance = chanceOf(entry)
            if (chance <= 0.0 || rng.nextDouble() * 100.0 >= chance) return@mapNotNull null
            Rolled(entry, amount(entry, rng))
        }

    fun amount(entry: DropEntry, rng: Random): Int =
        if (entry.maxAmount <= entry.minAmount) entry.minAmount else rng.nextInt(entry.minAmount, entry.maxAmount + 1)

    /** "평균 n번에 1번" 의 n. 0% 면 null. */
    fun oneIn(chance: Double): Long? = if (chance <= 0.0) null else ceil(100.0 / chance).toLong()
}
