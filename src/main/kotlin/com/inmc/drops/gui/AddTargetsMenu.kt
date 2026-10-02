package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.catalog.CropRule
import com.inmc.drops.table.BlockTwins
import com.inmc.drops.table.Category
import com.inmc.drops.table.DropTable
import com.inmc.drops.util.Labels
import com.inmc.drops.util.Ph
import kr.inmc.core.gui.Icon
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.type.CaveVinesPlant
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent

/**
 * 블록·작물 넣기 — 빈 칸에 블록 아이템을 올리고 확인. **올린 아이템은 돌려준다**(블록의 종류만 읽는다).
 *
 * - 블록, [table] 이 없을 때: 블록마다 표 하나(id = 블록 이름). 광석이면 심층암 쌍둥이도 같은 표에.
 * - 블록, [table] 이 있을 때: 그 표의 대상에 더한다.
 * - 작물: 목록에 없는 블록을 작물로. 규칙은 블록을 보고 — 열매 속성이 있으면 열매, 나이가 있으면 다 자람, 없으면 자연 생성.
 *
 * 이미 다른 블록 표에 있는 블록은 건너뛴다 — 한 블록은 한 표에만.
 */
class AddTargetsMenu(
    drops: Drops,
    viewer: Player,
    private val category: Category,
    private val table: DropTable?,
    override val back: () -> Unit,
) : Menu(drops, viewer, SIZE, "<dark_gray>${if (category == Category.CROP) "작물" else "블록"} 넣기</dark_gray>") {

    override fun draw() {
        clear()
        for (slot in INPUT_END until SIZE) set(slot, Icon.EDGE)
        set(SLOT_BACK, Icon.back()) {
            returnItems(0 until INPUT_END)
            back()
        }
        set(SLOT_HELP, Icon.of(Material.PAPER, "<yellow>도움말</yellow>",
            "<gray>빈 칸에 블록 아이템을 올리고 확인을 누르세요.</gray>",
            "<gray>올린 아이템은 그대로 돌려드립니다.</gray>",
            if (category == Category.BLOCK) "<gray>광석은 심층암 광석도 같이 들어갑니다.</gray>" else "<gray>규칙: 열매 속성 → 열매, 나이 → 다 자람, 그 밖 → 자연 생성</gray>"))
        set(SLOT_CONFIRM, Icon.confirm("<green>✔ 넣기</green>")) { confirm() }
    }

    override fun isSlotEditable(slot: Int): Boolean = slot < INPUT_END

    override fun acceptsShiftInsert(): Boolean = true

    override fun onClose(event: InventoryCloseEvent) = returnItems(0 until INPUT_END)

    private fun confirm() {
        val materials = (0 until INPUT_END).mapNotNull { inventory.getItem(it)?.type?.takeIf { type -> !type.isAir } }.distinct()
        returnItems(0 until INPUT_END)
        var added = 0
        for (material in materials) {
            if (!material.isBlock) {
                drops.messages.send(viewer, "not-a-block", Ph.of().item(Labels.of(material)))
                continue
            }
            added += if (category == Category.CROP) addCrop(material) else addBlock(material)
        }
        when {
            category == Category.CROP -> drops.messages.send(viewer, "crops-added", Ph.of().count(added))
            table == null -> drops.messages.send(viewer, "blocks-added", Ph.of().count(added))
            else -> drops.messages.send(viewer, "targets-added", Ph.of().count(added))
        }
        back()
    }

    private fun addBlock(material: Material): Int {
        drops.tables.block(material)?.let { taken ->
            drops.messages.send(viewer, "block-taken", Ph.of().item(Labels.of(material)).source(Labels.table(drops, taken)))
            return 0
        }
        val free = (listOf(material) + BlockTwins.of(material)).filter { drops.tables.block(it) == null }
        val keys = free.map { it.key.key }
        val target = table ?: drops.tables.obtain(Category.BLOCK, material.key.key, keys)
        for (key in keys) if (key !in target.targets) target.targets += key
        drops.tables.markDirty(target)
        return if (table == null) 1 else keys.size
    }

    private fun addCrop(material: Material): Int {
        if (drops.crops.of(material) != null) {
            drops.messages.send(viewer, "crop-known", Ph.of().item(Labels.of(material)))
            return 0
        }
        val data = runCatching { material.createBlockData() }.getOrNull()
        val rule = when (data) {
            is CaveVinesPlant -> CropRule.BERRIES
            is Ageable -> CropRule.AGE_MAX
            else -> CropRule.NATURAL
        }
        val key = material.key.key
        val crop = drops.tables.obtain(Category.CROP, key, listOf(key), rule)
        crop.rule = rule
        if (key !in crop.targets) crop.targets += key
        drops.tables.markDirty(crop)
        return 1
    }

    companion object {
        const val SIZE = 54
        const val INPUT_END = 45
        const val SLOT_BACK = 45
        const val SLOT_HELP = 49
        const val SLOT_CONFIRM = 53
    }
}
