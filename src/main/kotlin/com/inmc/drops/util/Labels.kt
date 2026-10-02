package com.inmc.drops.util

import com.inmc.drops.Drops
import com.inmc.drops.catalog.Group
import com.inmc.drops.catalog.Mobs
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropTable
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 이름을 MiniMessage 로. 바닐라 것은 `<lang:…>` — 서버는 한글 이름을 모르지만 클라이언트가 자기 언어로 그린다(상점 `util/Labels.kt` 와 같은 방식).
 */
object Labels {

    fun of(stack: ItemStack?): String {
        if (stack == null || stack.type.isAir) return "?"
        val meta = stack.itemMeta
        val custom = meta?.takeIf { it.hasDisplayName() }?.displayName()
        if (custom != null) return MiniMessage.miniMessage().serialize(custom)
        val item = meta?.takeIf { it.hasItemName() }?.itemName()
        if (item != null) return MiniMessage.miniMessage().serialize(item)
        return "<lang:" + stack.translationKey() + ">"
    }

    fun of(material: Material): String = "<lang:" + material.translationKey() + ">"

    /** 표의 이름 — 몹이면 그 몹, 작물이면 그 작물, 블록이면 첫 블록(여럿이면 "외 n"), 공통이면 그 이름. */
    fun table(drops: Drops, table: DropTable): String = when (table.category) {
        Category.MOB -> Mobs.of(table.id)?.let(Mobs::name) ?: table.id
        Category.CROP -> drops.crops.byId(table.id)?.name() ?: table.id
        Category.BLOCK -> {
            val first = table.targets.firstOrNull()?.let { Material.matchMaterial(it) }
            val rest = table.targets.size - 1
            (first?.let(::of) ?: table.id) + if (rest > 0) " <gray>외 $rest</gray>" else ""
        }
        Category.GROUP -> Group.of(table.id)?.label ?: table.id
    }
}
