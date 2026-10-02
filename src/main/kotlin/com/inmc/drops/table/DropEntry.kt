package com.inmc.drops.table

import kr.inmc.core.item.StoredItem
import kr.inmc.core.reward.RewardEntry
import org.bukkit.configuration.ConfigurationSection
import java.util.UUID

/**
 * 표의 한 줄 — urb 의 상자 보상(`box/Reward.kt`)과 같은 칸에 둘을 더했다.
 *
 * - [autoChance]: **작물 표에서만** 쓴다. 피스톤·물·주민이 캔 것(자동 농사)의 확률. 기본 0 = 자동 농사에서는 안 나온다
 *   — 아무도 정하지 않았는데 자동 농장이 커스텀 아이템을 찍어 내는 일이 없게.
 * - [hidden]: `/드랍 정보` 에 안 보인다(숨겨 둔 희귀 드랍).
 *
 * 확률은 0 을 받는다(잠깐 끄기 — 지우면 아이템·개수·명령어를 잃는다). 그 밖은 0.01~100.
 */
class DropEntry(
    val id: String = newId(),
    var item: StoredItem,
    chance: Double = 10.0,
    autoChance: Double = 0.0,
    minAmount: Int = 1,
    maxAmount: Int = 1,
    var announce: Boolean = false,
    var hidden: Boolean = false,
    var commands: MutableList<String> = mutableListOf(),
    var giveItem: Boolean = true,
) {

    var chance: Double = RewardEntry.clampRewardChance(chance)
        set(value) {
            field = RewardEntry.clampRewardChance(value)
        }

    var autoChance: Double = RewardEntry.clampRewardChance(autoChance)
        set(value) {
            field = RewardEntry.clampRewardChance(value)
        }

    var minAmount: Int = minAmount.coerceIn(1, MAX_AMOUNT)
        set(value) {
            field = value.coerceIn(1, MAX_AMOUNT)
            if (field > maxAmount) maxAmount = field
        }

    var maxAmount: Int = maxAmount.coerceIn(this.minAmount, MAX_AMOUNT)
        set(value) {
            field = value.coerceIn(1, MAX_AMOUNT)
            if (field < minAmount) minAmount = field
        }

    /** 아무것도 안 하는 줄 — 아이템을 안 주고 명령어도 없다. 굴리지 않는다. */
    fun isEmpty(): Boolean = !giveItem && commands.isEmpty()

    fun label(): String = item.label()

    fun save(section: ConfigurationSection) {
        item.save(section)
        section.set("chance", chance)
        if (autoChance > 0.0) section.set("auto-chance", autoChance)
        section.set("min-amount", minAmount)
        section.set("max-amount", maxAmount)
        if (announce) section.set("announce", true)
        if (hidden) section.set("hidden", true)
        if (!giveItem) section.set("give-item", false)
        if (commands.isNotEmpty()) section.set("commands", commands)
    }

    companion object {

        /** 한 번에 떨굴 수 있는 최대 개수. 그보다 많으면 땅이 아이템으로 덮인다. */
        const val MAX_AMOUNT = 64

        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

        /** 아이템 칸이 비었으면(참조도 스냅샷도 없으면) null — 지킬 내용이 없는 줄이다. */
        fun load(id: String, section: ConfigurationSection): DropEntry? {
            val item = StoredItem.load(section) ?: return null
            return DropEntry(
                id = id,
                item = item,
                chance = section.getDouble("chance", 10.0),
                autoChance = section.getDouble("auto-chance", 0.0),
                minAmount = section.getInt("min-amount", 1),
                maxAmount = section.getInt("max-amount", section.getInt("min-amount", 1)),
                announce = section.getBoolean("announce", false),
                hidden = section.getBoolean("hidden", false),
                commands = section.getStringList("commands").toMutableList(),
                giveItem = section.getBoolean("give-item", true),
            )
        }
    }
}
