package com.inmc.drops.config

import com.inmc.drops.table.Category
import org.bukkit.configuration.file.YamlConfiguration

/** 월드 하나에서 무엇이 켜져 있는가. 적지 않은 월드는 전부 켜짐이다. */
data class WorldRule(val mob: Boolean = true, val crop: Boolean = true, val block: Boolean = true) {

    fun allows(category: Category): Boolean = when (category) {
        Category.MOB -> mob
        Category.CROP -> crop
        Category.BLOCK -> block
        Category.GROUP -> true
    }

    fun with(category: Category, value: Boolean): WorldRule = when (category) {
        Category.MOB -> copy(mob = value)
        Category.CROP -> copy(crop = value)
        Category.BLOCK -> copy(block = value)
        Category.GROUP -> this
    }

    val isDefault: Boolean get() = mob && crop && block
}

/**
 * `config.yml`. 화면에서 고치면 [toYaml] 로 통째로 다시 쓴다.
 *
 * 월드별 설정은 **목록**으로 적는다 — 월드 이름을 키로 쓰면 이름에 점이 든 월드가 `getKeys(false)` 에서 사라진다(지뢰 1).
 */
data class DropsConfig(
    /** 새로 등록한 항목의 처음 확률(%). */
    val defaultChance: Double = 10.0,
    /** 드랍을 만든 사람만 이 시간(초) 동안 주울 수 있다. 0 = 끔. */
    val protectSeconds: Int = 5,
    /** 청크 하나에서 1시간 동안 자동 농사로 나올 수 있는 커스텀 드랍 수. [UNLIMITED] = 무제한, 0 = 자동 농사에서는 안 나옴. */
    val autoCap: Int = 64,
    val worlds: Map<String, WorldRule> = emptyMap(),
) {

    fun world(name: String): WorldRule = worlds[name] ?: WorldRule()

    fun allows(world: String, category: Category): Boolean = world(world).allows(category)

    fun withWorld(name: String, rule: WorldRule): DropsConfig =
        copy(worlds = if (rule.isDefault) worlds - name else worlds + (name to rule))

    fun toYaml(): YamlConfiguration = YamlConfiguration().also { y ->
        y.options().setHeader(HEADER.lines())
        y.set("default-chance", defaultChance)
        y.set("pickup-protect-seconds", protectSeconds)
        y.set("auto-farm.cap-per-chunk-hour", autoCap)
        y.set("worlds", worlds.map { (name, rule) -> linkedMapOf("name" to name, "mob" to rule.mob, "crop" to rule.crop, "block" to rule.block) })
    }

    companion object {

        const val UNLIMITED = -1

        val HEADER = """
            inmc-drops 설정. 게임 안 /드랍 → 설정 에서 고치는 것이 편합니다(고치면 이 파일을 다시 씁니다).
            worlds: 적지 않은 월드는 몹·작물·블록 전부 켜짐. 끄고 싶은 월드만 적습니다.
        """.trimIndent()

        fun from(config: YamlConfiguration): DropsConfig {
            val worlds = LinkedHashMap<String, WorldRule>()
            for (raw in config.getMapList("worlds")) {
                val name = raw["name"]?.toString()?.takeIf { it.isNotBlank() } ?: continue
                fun flag(key: String) = (raw[key] as? Boolean) ?: raw[key]?.toString()?.toBooleanStrictOrNull() ?: true
                worlds[name] = WorldRule(flag("mob"), flag("crop"), flag("block"))
            }
            return DropsConfig(
                defaultChance = config.getDouble("default-chance", 10.0).coerceIn(0.0, 100.0),
                protectSeconds = config.getInt("pickup-protect-seconds", 5).coerceAtLeast(0),
                autoCap = config.getInt("auto-farm.cap-per-chunk-hour", 64).coerceAtLeast(UNLIMITED),
                worlds = worlds,
            )
        }
    }
}
