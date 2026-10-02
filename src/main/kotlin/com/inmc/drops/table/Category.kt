package com.inmc.drops.table

/**
 * 표의 종류. 디스크에는 [id] 를 쓴다 — 순서값을 쓰면 나중에 종류를 끼울 때 저장된 값 전부가 다른 뜻이 된다(지뢰 5).
 *
 * [GROUP] 은 공통 표(모든 몹·적대 몹·동물·모든 작물)다. 월드 켜짐은 표가 아니라 **사건의 종류**(몹·작물·블록)로 본다.
 */
enum class Category(val id: String, val label: String) {
    MOB("mob", "몹"),
    CROP("crop", "작물"),
    BLOCK("block", "블록"),
    GROUP("group", "공통"),
    ;

    companion object {
        fun parse(raw: String?): Category? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}
