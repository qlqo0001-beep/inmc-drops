package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.config.DropsConfig
import com.inmc.drops.config.WorldRule
import com.inmc.drops.table.Category
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Numbers
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player

/** 전체 설정 — 기본 확률·줍기 보호·자동 농사 상한·월드별. 고치면 `config.yml` 을 다시 쓴다. */
class SettingsMenu(drops: Drops, viewer: Player) : Menu(drops, viewer, SIZE, "<dark_gray>드랍 설정</dark_gray>") {

    override val back: () -> Unit = { MainMenu(drops, viewer).show() }

    override fun draw() {
        clear()
        val c = drops.config
        set(SLOT_CHANCE, valueIcon(Material.PAPER, "새 항목의 확률", "${Numbers.chance(c.defaultChance)}%", "<gray>표에 아이템을 올렸을 때 처음 확률.</gray>")) {
            ask(DialogForm("<yellow>새 항목의 확률</yellow>").decimal("chance", "확률(%)", c.defaultChance, 0.0, 100.0)) { v ->
                v.decimal("chance")?.let { value -> drops.updateConfig { it.copy(defaultChance = value) } }
            }
        }
        set(SLOT_PROTECT, valueIcon(Material.SHIELD, "줍기 보호", if (c.protectSeconds > 0) "${c.protectSeconds}초" else "끔",
            "<gray>드랍을 만든 사람만 이 시간 동안 주울 수 있습니다.</gray>", "<dark_gray>0 = 끔. 자동 농사 드랍은 주인이 없습니다.</dark_gray>")) {
            ask(DialogForm("<yellow>줍기 보호</yellow>").long("seconds", "초(0 = 끔)", c.protectSeconds.toLong(), 0, 600)) { v ->
                v.long("seconds")?.let { value -> drops.updateConfig { it.copy(protectSeconds = value.toInt()) } }
            }
        }
        set(SLOT_CAP, valueIcon(Material.PISTON, "자동 농사 상한", capText(c.autoCap),
            "<gray>청크 하나에서 1시간 동안 자동 농사로 나올 수 있는 개수.</gray>",
            "<gray>자동 농장은 사람이 없어도 돌아서, 상한이 없으면 끝없이 나옵니다.</gray>",
            "<dark_gray>-1 = 무제한, 0 = 자동 농사에서는 안 나옴</dark_gray>")) {
            ask(DialogForm("<yellow>자동 농사 상한</yellow>").long("cap", "청크당 1시간 개수(-1 = 무제한)", c.autoCap.toLong(), DropsConfig.UNLIMITED.toLong(), 100_000)) { v ->
                v.long("cap")?.let { value -> drops.updateConfig { it.copy(autoCap = value.toInt()) } }
            }
        }
        val off = c.worlds.count { !it.value.isDefault }
        set(SLOT_WORLDS, Icon.of(Material.GRASS_BLOCK, "<yellow>월드별 켜고 끄기</yellow>",
            "<gray>월드마다 몹·작물·블록 드랍을 따로 끕니다.</gray>",
            "<gray>끈 것이 있는 월드: <white>${off}개</white></gray>", "", "<yellow>▶ 클릭</yellow>")) { WorldsMenu(drops, viewer).show() }

        fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    private fun capText(cap: Int): String = when {
        cap < 0 -> "무제한"
        cap == 0 -> "안 나옴"
        else -> "${cap}개"
    }

    companion object {
        const val SIZE = 27
        const val SLOT_CHANCE = 10
        const val SLOT_PROTECT = 12
        const val SLOT_CAP = 14
        const val SLOT_WORLDS = 16
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26
    }
}

/** 서버의 월드 목록. */
class WorldsMenu(drops: Drops, viewer: Player, private var page: Int = 0) : Menu(drops, viewer, SIZE, "<dark_gray>월드별 켜고 끄기</dark_gray>") {

    override val back: () -> Unit = { SettingsMenu(drops, viewer).show() }

    override fun draw() {
        clear()
        val worlds = Bukkit.getWorlds()
        page = Paging.clamp(page, worlds.size)
        for ((slot, world) in Paging.slice(worlds, page).withIndex()) {
            val rule = drops.config.world(world.name)
            set(slot, Icon.of(icon(world), "<white>${world.name}</white>", rule(rule) + listOf("", "<yellow>▶ 클릭</yellow>"))) {
                WorldMenu(drops, viewer, world.name) { WorldsMenu(drops, viewer, page).show() }.show()
            }
        }
        fillEmpty(Icon.FILLER)
        pager(page, worlds.size) { page = it; refresh() }
        navigation()
    }

    private fun icon(world: World): Material = when (world.environment) {
        World.Environment.NETHER -> Material.NETHERRACK
        World.Environment.THE_END -> Material.END_STONE
        else -> Material.GRASS_BLOCK
    }

    companion object {
        const val SIZE = 54

        fun rule(rule: WorldRule): List<String> = listOf(
            "<gray>몹: </gray>" + Icon.toggle(rule.mob),
            "<gray>작물: </gray>" + Icon.toggle(rule.crop),
            "<gray>블록: </gray>" + Icon.toggle(rule.block),
        )
    }
}

/** 월드 하나 — 몹·작물·블록을 따로. */
class WorldMenu(drops: Drops, viewer: Player, private val world: String, override val back: () -> Unit) :
    Menu(drops, viewer, SIZE, "<dark_gray>월드 <gray>|</gray> $world</dark_gray>") {

    override fun draw() {
        clear()
        val rule = drops.config.world(world)
        toggle(SLOT_MOB, Category.MOB, rule)
        toggle(SLOT_CROP, Category.CROP, rule)
        toggle(SLOT_BLOCK, Category.BLOCK, rule)
        fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    private fun toggle(slot: Int, category: Category, rule: WorldRule) {
        val on = rule.allows(category)
        set(slot, toggleIcon("${category.label} 드랍", on, "<gray>이 월드에서 ${category.label} 커스텀 드랍을 굴릴지.</gray>")) {
            drops.updateConfig { it.withWorld(world, it.world(world).with(category, !on)) }
            refresh()
        }
    }

    companion object {
        const val SIZE = 27
        const val SLOT_MOB = 11
        const val SLOT_CROP = 13
        const val SLOT_BLOCK = 15
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26
    }
}
