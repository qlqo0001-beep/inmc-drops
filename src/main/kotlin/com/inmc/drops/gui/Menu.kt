package com.inmc.drops.gui

import com.inmc.drops.Drops
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * core [kr.inmc.core.gui.Menu] 에 이 플러그인의 로케이터와 보는 사람을 붙인 얇은 층(상점 `gui/Menu.kt` 와 같은 모양).
 * 리로드가 열린 화면을 닫을 때 [owner] 로 우리 것을 가려낸다.
 *
 * 값 입력은 채팅이 아니라 **입력창(Dialog)** — [ask]. 채팅 입력의 "메시지 키 넷" 함정이 없다(지뢰 6).
 */
abstract class Menu(
    protected val drops: Drops,
    protected val viewer: Player,
    size: Int,
    title: String,
) : kr.inmc.core.gui.Menu(size, Text.renderFlat(title)) {

    override val owner: Any get() = drops

    /** 뒤로 버튼이 여는 화면. null 이면 뒤로 버튼이 없다. */
    protected open val back: (() -> Unit)? = null

    protected fun navigation(backSlot: Int = Paging.SLOT_BACK, closeSlot: Int = Paging.SLOT_CLOSE) {
        back?.let { go -> set(backSlot, Icon.back()) { go() } }
        set(closeSlot, Icon.close()) { viewer.closeInventory() }
    }

    fun show() = open(viewer)

    /** 입력창을 띄운다. 확인하면 [onSubmit] 뒤에 이 화면을 다시 연다(그 안에서 다른 화면을 열면 그쪽이 이긴다). */
    protected fun ask(form: DialogForm, reopen: () -> Unit = { show() }, onSubmit: (DialogForm.Values) -> Unit) {
        form.show(drops.plugin, viewer, onCancel = { reopen() }) { _, values ->
            onSubmit(values)
            if (viewer.openInventory.topInventory.holder !is kr.inmc.core.gui.Menu) reopen()
        }
    }

    protected fun pager(page: Int, total: Int, perPage: Int = Paging.PER_PAGE, go: (Int) -> Unit) {
        val pages = Paging.pageCount(total, perPage)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { go(page - 1) }
        if (page < pages - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { go(page + 1) }
    }

    protected fun toggleIcon(name: String, value: Boolean, vararg lore: String): ItemStack =
        Icon.of(Icon.toggleMaterial(value), "<yellow>$name: </yellow>" + Icon.toggle(value), lore.toList() + listOf("", "<gray>클릭해서 바꾸기</gray>"))

    protected fun valueIcon(material: Material, name: String, value: String, vararg lore: String): ItemStack =
        Icon.of(material, "<yellow>$name: </yellow><white>$value</white>", lore.toList() + listOf("", "<gray>클릭해서 바꾸기</gray>"))

    /** 이 화면의 편집 칸에 올려 둔 아이템을 가방으로(넘치면 발밑에). 확인하지 않고 닫거나 나갈 때. */
    protected fun returnItems(slots: IntRange) {
        for (slot in slots) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir) continue
            inventory.setItem(slot, null)
            viewer.inventory.addItem(stack).values.forEach { viewer.world.dropItemNaturally(viewer.location, it) }
        }
    }
}
