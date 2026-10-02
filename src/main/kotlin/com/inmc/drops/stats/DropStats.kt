package com.inmc.drops.stats

import com.inmc.drops.table.Category
import kr.inmc.core.config.ConfigService
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 항목별로 지금까지 나온 횟수 — `stats.yml`. 확률을 조정할 근거로 항목 화면에 보인다.
 *
 * 열쇠는 `<종류>.<표>.<항목>` 이다. 표 id 는 [kr.inmc.core.store.DefinitionKey] 라 점이 없고 항목 id 는 16진수 8자다.
 */
class DropStats(io: ConfigService) : YamlFileStore(io, listOf("stats.yml"), HEADER, "드랍 통계") {

    private val counts = HashMap<String, Long>()

    private fun key(category: Category, table: String, entry: String) = category.id + "." + table + "." + entry

    fun add(category: Category, table: String, entry: String) {
        counts.merge(key(category, table, entry), 1L, Long::plus)
        markDirty()
    }

    fun get(category: Category, table: String, entry: String): Long = counts[key(category, table, entry)] ?: 0L

    fun reset(category: Category, table: String, entry: String) {
        if (counts.remove(key(category, table, entry)) != null) markDirty()
    }

    override fun read(config: YamlConfiguration) {
        counts.clear()
        for (category in config.getKeys(false)) {
            val tables = config.getConfigurationSection(category) ?: continue
            for (table in tables.getKeys(false)) {
                val entries = tables.getConfigurationSection(table) ?: continue
                for (entry in entries.getKeys(false)) counts["$category.$table.$entry"] = entries.getLong(entry)
            }
        }
    }

    override fun write(config: YamlConfiguration) {
        for ((key, value) in counts) config.set(key, value)
    }

    private companion object {
        const val HEADER = "inmc-drops 항목별로 지금까지 나온 횟수. 화면(/드랍)이 읽는다 — 손으로 고칠 일은 없다."
    }
}
