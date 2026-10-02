package com.inmc.drops.table

import com.inmc.drops.catalog.CropRule
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 드랍 표 하나 — `tables/<종류>/<id>.yml`.
 *
 * - 몹 표: id = 엔티티 종류 키(`zombie`). [targets] 는 비어 있다.
 * - 작물 표: id = 작물 이름(`wheat`·`kelp`). 카탈로그의 작물이면 [rule] 이 없다(카탈로그가 정한다). 관리자가 추가한 작물만
 *   [targets]·[rule] 을 갖는다.
 * - 블록 표: id = 처음 넣은 블록(`diamond_ore`). [targets] 에 블록 여럿(광석과 심층암 쌍둥이 …).
 * - 공통 표: id = `mob-all` 같은 고정 이름.
 *
 * 항목은 id 를 열쇠로 적는다 — 순서가 그대로 보존되고, 한 줄이 깨져도 그 줄만 빠진다.
 */
class DropTable(
    val category: Category,
    val id: String,
    targets: Collection<String> = emptyList(),
    var rule: CropRule? = null,
) {

    /** 블록·작물 이름(소문자 `diamond_ore`). `Material.matchMaterial` 로 읽는다. */
    val targets: MutableList<String> = targets.map { it.lowercase() }.distinct().toMutableList()

    var enabled: Boolean = true

    /** 행운·약탈 레벨 하나당 확률을 몇 % 올리는가(배율 — 10% 항목에 50 이면 행운 III 에서 25%). 0 = 안 올림. */
    var bonus: Double = 0.0
        set(value) {
            field = value.coerceIn(0.0, MAX_BONUS)
        }

    val entries: MutableList<DropEntry> = mutableListOf()

    /** 기본값과 다른 것이 하나라도 있는가 — 목록의 "설정된 것만" 이 이것을 본다. */
    val isConfigured: Boolean get() = entries.isNotEmpty() || !enabled || bonus > 0.0

    fun save(): YamlConfiguration = YamlConfiguration().also { y ->
        y.set("category", category.id)
        if (targets.isNotEmpty()) y.set("targets", targets)
        rule?.let { y.set("rule", it.id) }
        y.set("enabled", enabled)
        if (bonus > 0.0) y.set("bonus", bonus)
        val section = y.createSection("entries")
        for (entry in entries) entry.save(section.createSection(entry.id))
    }

    companion object {

        const val MAX_BONUS = 1000.0

        /** 파일 이름의 종류와 안의 `category` 가 다르면 안 읽는다 — 손으로 옮긴 파일이 엉뚱한 곳에서 돌지 않게. */
        fun load(category: Category, id: String, config: YamlConfiguration): DropTable? {
            val written = Category.parse(config.getString("category"))
            if (written != null && written != category) return null
            val table = DropTable(category, id, config.getStringList("targets"), CropRule.parse(config.getString("rule")))
            table.enabled = config.getBoolean("enabled", true)
            table.bonus = config.getDouble("bonus", 0.0)
            val section = config.getConfigurationSection("entries")
            if (section != null) {
                for (key in section.getKeys(false)) {
                    val entry = section.getConfigurationSection(key)?.let { DropEntry.load(key, it) } ?: continue
                    table.entries.add(entry)
                }
            }
            return table
        }
    }
}
