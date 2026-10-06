package com.inmc.drops.gui

import com.inmc.drops.Drops
import com.inmc.drops.table.Category
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Numbers
import org.bukkit.Material
import org.bukkit.entity.Player

/** `/드랍` 관리 화면의 첫 화면. */
class MainMenu(drops: Drops, viewer: Player) : Menu(drops, viewer, SIZE, "<dark_gray>커스텀 드랍</dark_gray>") {

    override fun draw() {
        clear()
        category(SLOT_MOBS, Material.ZOMBIE_HEAD, Category.MOB, "모든 생물이 자동으로 들어 있습니다.", "플레이어가 직접 처치했을 때만 나옵니다.")
        category(SLOT_CROPS, Material.WHEAT, Category.CROP, "다 자란 작물을 캘 때만 나옵니다.", "자동 농사는 항목마다 따로 정한 확률로.")
        category(SLOT_BLOCKS, Material.DIAMOND_ORE, Category.BLOCK, "등록한 블록만 — [블록 추가]로 넣습니다.", "놓은 블록·섬세한 손길은 안 나옵니다.")
        category(SLOT_GROUPS, Material.CHEST, Category.GROUP, "모든 몹·적대 몹·동물·모든 작물에", "한 번에 거는 표입니다.")

        set(SLOT_SETTINGS, Icon.of(Material.COMPARATOR, "<yellow>설정</yellow>",
            "<gray>월드별 켜고 끄기 · 줍기 보호 · 자동 농사 상한 · 기본 확률</gray>")) { SettingsMenu(drops, viewer).show() }

        val boost = drops.boost
        set(SLOT_BOOST, Icon.of(if (boost.active()) Material.NETHER_STAR else Material.FIREWORK_ROCKET, "<gold>드랍 이벤트</gold>",
            buildList {
                if (boost.active()) {
                    add("<green>진행 중</green> <gray>— ×${Numbers.chance(boost.multiplier)}, 남은 시간 ${boost.remainingText()}</gray>")
                } else {
                    add("<gray>진행 중인 이벤트가 없습니다.</gray>")
                }
                add("<gray>잠깐 모든 커스텀 드랍 확률을 올립니다.</gray>")
            })) { BoostMenu(drops, viewer).show() }

        set(SLOT_HUB, Icon.of(Material.COMPASS, "<gold>어드민 메뉴로</gold>",
            "<gray>각 플러그인 설정 허브로 돌아갑니다.</gray>")) { viewer.performCommand("메뉴 어드민") }

        fillEmpty(Icon.FILLER)
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun category(slot: Int, material: Material, category: Category, vararg lore: String) {
        val configured = drops.tables.all(category).count { it.isConfigured }
        set(slot, Icon.of(material, "<green>${category.label}</green>", lore.toList() + listOf("", "<gray>설정된 표: <white>${configured}개</white></gray>", "<yellow>▶ 클릭</yellow>"))) {
            TargetListMenu(drops, viewer, category).show()
        }
    }

    companion object {
        const val SIZE = 27
        const val SLOT_MOBS = 10
        const val SLOT_CROPS = 12
        const val SLOT_BLOCKS = 14
        const val SLOT_GROUPS = 16
        const val SLOT_SETTINGS = 21
        const val SLOT_BOOST = 23
        const val SLOT_HUB = 18
        const val SLOT_CLOSE = 26
    }
}
