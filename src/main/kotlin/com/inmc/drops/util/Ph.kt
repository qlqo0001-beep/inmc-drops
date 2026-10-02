package com.inmc.drops.util

import kr.inmc.core.util.TokenBag

/**
 * 메시지·명령어 한 번 렌더링에 쓰이는 토큰 주머니. 한글/영문 둘 다 받는다 — 다른 INMC 플러그인들과 같은 관례.
 * 명령어의 `{플레이어네임}` 은 urb 보상 명령어와 같은 자리표시다(같은 서버의 관리자가 같은 글자를 친다).
 */
class Ph : TokenBag<Ph>() {

    override val aliases: Map<String, List<String>> get() = ALIASES

    fun player(name: String): Ph = put(PLAYER, name)

    fun item(name: String): Ph = put(ITEM, name)

    fun source(name: String): Ph = put(SOURCE, name)

    fun value(text: String): Ph = put(VALUE, text)

    fun count(value: Int): Ph = put(COUNT, value.toString())

    fun amount(text: String): Ph = put(AMOUNT, text)

    companion object {

        fun of(): Ph = Ph()

        const val PLAYER = "player"
        const val ITEM = "item"
        const val SOURCE = "source"
        const val VALUE = "value"
        const val COUNT = "count"
        const val AMOUNT = "amount"

        private val ALIASES: Map<String, List<String>> = mapOf(
            PLAYER to listOf("{플레이어네임}", "{플레이어}", "{player}"),
            ITEM to listOf("{아이템}", "{item}"),
            SOURCE to listOf("{출처}", "{source}"),
            VALUE to listOf("{값}", "{value}"),
            COUNT to listOf("{개수}", "{count}"),
            AMOUNT to listOf("{수량}", "{amount}"),
        )
    }
}
