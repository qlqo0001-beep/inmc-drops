package com.inmc.drops.catalog

/**
 * 작물을 "지금 캐면 나오는가"로 가르는 규칙. 서버 없이 도는 순수 판정이다 — 블록에서 값을 꺼내는 것은 부르는 쪽.
 *
 * [NATURAL] 은 언제나 true 다. 그 작물은 **놓은 블록이 아닐 때만** 나오는데, 그것은 청크 기록이 따로 본다.
 */
enum class CropRule(val id: String, val label: String, val description: String) {
    AGE_MAX("age-max", "다 자람", "나이가 최대일 때만 나옵니다."),
    BERRIES("berries", "열매", "열매가 달려 있을 때만 나옵니다(우클릭 수확 포함)."),
    NATURAL("natural", "자연 생성", "놓은 블록이 아닐 때만 나옵니다(줄기에 맺히거나 자라난 것)."),
    ;

    /**
     * @param age 나이가 있는 블록의 나이, 없으면 null
     * @param maxAge 그 블록의 최대 나이
     * @param berries 열매 속성이 있는 블록(발광 열매 덩굴)의 값, 없으면 null
     */
    fun ready(age: Int?, maxAge: Int?, berries: Boolean?): Boolean = when (this) {
        AGE_MAX -> age != null && maxAge != null && age >= maxAge
        BERRIES -> berries ?: (age != null && age >= SWEET_BERRY_RIPE)
        NATURAL -> true
    }

    companion object {

        /** 달콤한 열매 덤불은 나이 2 부터 열매가 달린다(우클릭 수확이 되는 나이). */
        const val SWEET_BERRY_RIPE = 2

        fun parse(raw: String?): CropRule? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}
