package com.inmc.drops.table

import org.bukkit.Material

/**
 * 광석의 심층암 쌍둥이. 다이아몬드 광석을 등록하면 심층암 다이아몬드 광석도 같은 표에 넣는다 — 높이만 다른 같은 광석이라
 * 따로 등록하는 것을 잊으면 깊은 곳에서만 안 나온다.
 */
object BlockTwins {

    fun of(material: Material): List<Material> {
        val name = material.name
        if (!name.endsWith("_ORE")) return emptyList()
        val other = if (name.startsWith("DEEPSLATE_")) name.removePrefix("DEEPSLATE_") else "DEEPSLATE_$name"
        return listOfNotNull(Material.matchMaterial(other))
    }
}
