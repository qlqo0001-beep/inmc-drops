package com.inmc.drops.catalog

import org.bukkit.Material
import org.bukkit.Tag

/**
 * 작물 한 종류. 블록이 둘인 작물이 있다(켈프·켈프 줄기, 발광 열매 덩굴·덩굴 줄기).
 *
 * [custom] 은 관리자가 "작물 추가"로 넣은 것 — 그 표 파일에 규칙과 블록이 적혀 있다.
 */
data class CropKind(
    val id: String,
    val materials: List<Material>,
    val rule: CropRule,
    val icon: Material,
    val custom: Boolean = false,
) {
    /** `<lang:…>` — 클라이언트가 자기 언어로 그린다. */
    fun name(): String = "<lang:" + icon.translationKey() + ">"
}

/**
 * 작물 목록. 기본 목록 + **켤 때 `#minecraft:crops` 태그에서 새로 보이는 것**(새 버전의 작물) + 관리자가 추가한 것.
 *
 * - 줄기(`_STEM`)는 뺀다 — 줄기를 캐는 것은 수확이 아니다. 수박·호박은 열매 블록이 작물이다.
 * - 횃불꽃 작물(`TORCHFLOWER_CROP`)은 뺀다 — 다 자라면 횃불꽃(`TORCHFLOWER`)으로 바뀌고 그것을 캔다.
 * - 후렴초는 넣지 않는다 — 커스텀아이템이 커스텀 블록에 빌려 쓴다.
 */
class Crops {

    @Volatile
    private var kinds: List<CropKind> = BUILT_IN

    @Volatile
    private var byMaterial: Map<Material, CropKind> = index(BUILT_IN)

    fun all(): List<CropKind> = kinds

    fun of(material: Material): CropKind? = byMaterial[material]

    fun byId(id: String): CropKind? = kinds.firstOrNull { it.id == id }

    /** [NATURAL][CropRule.NATURAL] 작물의 블록 — 놓은 블록 기록이 따라다닐 블록. */
    fun naturalMaterials(): Set<Material> = kinds.filter { it.rule == CropRule.NATURAL }.flatMap { it.materials }.toSet()

    /** 태그(서버가 있어야 읽힌다)와 관리자가 추가한 작물로 다시 짠다. 기본 목록과 겹치는 블록은 기본 목록이 이긴다. */
    fun refresh(custom: List<CropKind>) {
        val known = BUILT_IN.flatMap { it.materials }.toMutableSet()
        val list = BUILT_IN.toMutableList()
        val tagged = runCatching { Tag.CROPS.values }.getOrDefault(emptySet())
        for (material in tagged.sortedBy { it.name }) {
            if (material in known || material.name.endsWith("_STEM") || material in SKIPPED) continue
            list += CropKind(material.key.key, listOf(material), CropRule.AGE_MAX, if (material.isItem) material else Material.WHEAT_SEEDS)
            known += material
        }
        for (kind in custom) {
            if (kind.materials.any { it in known }) continue
            list += kind
            known += kind.materials
        }
        kinds = list
        byMaterial = index(list)
    }

    companion object {

        private val SKIPPED = setOf(Material.TORCHFLOWER_CROP, Material.CHORUS_FLOWER, Material.CHORUS_PLANT)

        val BUILT_IN: List<CropKind> = listOf(
            CropKind("wheat", listOf(Material.WHEAT), CropRule.AGE_MAX, Material.WHEAT),
            CropKind("carrots", listOf(Material.CARROTS), CropRule.AGE_MAX, Material.CARROT),
            CropKind("potatoes", listOf(Material.POTATOES), CropRule.AGE_MAX, Material.POTATO),
            CropKind("beetroots", listOf(Material.BEETROOTS), CropRule.AGE_MAX, Material.BEETROOT),
            CropKind("nether_wart", listOf(Material.NETHER_WART), CropRule.AGE_MAX, Material.NETHER_WART),
            CropKind("cocoa", listOf(Material.COCOA), CropRule.AGE_MAX, Material.COCOA_BEANS),
            CropKind("pitcher_crop", listOf(Material.PITCHER_CROP), CropRule.AGE_MAX, Material.PITCHER_PLANT),
            CropKind("sweet_berries", listOf(Material.SWEET_BERRY_BUSH), CropRule.BERRIES, Material.SWEET_BERRIES),
            CropKind("glow_berries", listOf(Material.CAVE_VINES, Material.CAVE_VINES_PLANT), CropRule.BERRIES, Material.GLOW_BERRIES),
            CropKind("melon", listOf(Material.MELON), CropRule.NATURAL, Material.MELON),
            CropKind("pumpkin", listOf(Material.PUMPKIN), CropRule.NATURAL, Material.PUMPKIN),
            CropKind("sugar_cane", listOf(Material.SUGAR_CANE), CropRule.NATURAL, Material.SUGAR_CANE),
            CropKind("cactus", listOf(Material.CACTUS), CropRule.NATURAL, Material.CACTUS),
            CropKind("bamboo", listOf(Material.BAMBOO), CropRule.NATURAL, Material.BAMBOO),
            CropKind("kelp", listOf(Material.KELP, Material.KELP_PLANT), CropRule.NATURAL, Material.KELP),
            CropKind("torchflower", listOf(Material.TORCHFLOWER), CropRule.NATURAL, Material.TORCHFLOWER),
        )

        private fun index(kinds: List<CropKind>): Map<Material, CropKind> =
            kinds.flatMap { kind -> kind.materials.map { it to kind } }.toMap()
    }
}
