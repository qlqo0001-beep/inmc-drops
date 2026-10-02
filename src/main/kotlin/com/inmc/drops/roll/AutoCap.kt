package com.inmc.drops.roll

/**
 * 자동 농사 상한 — 청크 하나에서 1시간 동안 나올 수 있는 커스텀 드랍 수. 자동 농장은 사람이 없어도 돌아서, 상한이 없으면
 * 커스텀 아이템을 끝없이 찍어 낸다.
 *
 * 창은 청크마다 처음 센 때부터 1시간(고정 창). 재시작하면 처음부터 센다 — 저장할 만큼 정확할 필요는 없다.
 * 메인 스레드에서만 부른다.
 */
class AutoCap(private val windowMillis: Long = HOUR) {

    private class Window(var start: Long, var count: Int)

    private val windows = HashMap<String, Window>()

    /** 지금 더 나올 수 있는가. [cap] 이 음수면 무제한, 0 이면 늘 아니다. */
    fun allows(chunk: String, now: Long, cap: Int): Boolean {
        if (cap < 0) return true
        if (cap == 0) return false
        val window = current(chunk, now) ?: return true
        return window.count < cap
    }

    /** 나온 만큼 센다. */
    fun add(chunk: String, now: Long, amount: Int) {
        if (amount <= 0) return
        val window = current(chunk, now) ?: Window(now, 0).also { windows[chunk] = it }
        window.count += amount
    }

    fun count(chunk: String, now: Long): Int = current(chunk, now)?.count ?: 0

    /** 지난 창을 버린다(틱커가 가끔). */
    fun prune(now: Long) {
        windows.values.removeIf { now - it.start >= windowMillis }
    }

    private fun current(chunk: String, now: Long): Window? {
        val window = windows[chunk] ?: return null
        if (now - window.start >= windowMillis) {
            windows.remove(chunk)
            return null
        }
        return window
    }

    companion object {
        const val HOUR = 60L * 60L * 1000L
    }
}
