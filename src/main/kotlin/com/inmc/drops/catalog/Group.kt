package com.inmc.drops.catalog

import com.inmc.drops.table.Category
import org.bukkit.Material
import org.bukkit.entity.EntityType

/**
 * 공통 표 — 여러 종류에 한 번에 거는 표. 몹 하나가 죽으면 그 몹의 표와 맞는 공통 표를 **각각 따로** 굴린다.
 * 예: 적대 몹 전체에 "미확인 부여서 1%" — 이것이 없으면 몹 70여 종에 하나씩 넣어야 한다.
 *
 * [id] 는 파일 이름이다(`tables/group/mob-all.yml`). 바꾸면 그 표를 잃는다.
 */
enum class Group(val id: String, val label: String, val icon: Material, val kind: Category, val description: String) {
    MOB_ALL("mob-all", "모든 몹", Material.SPAWNER, Category.MOB, "어떤 몹이든"),
    MOB_HOSTILE("mob-hostile", "적대 몹", Material.ZOMBIE_HEAD, Category.MOB, "좀비·스켈레톤처럼 공격하는 몹"),
    MOB_ANIMAL("mob-animal", "동물", Material.LEAD, Category.MOB, "소·돼지·양처럼 번식하는 동물"),
    CROP_ALL("crop-all", "모든 작물", Material.HAY_BLOCK, Category.CROP, "어떤 작물이든"),
    ;

    fun appliesTo(type: EntityType): Boolean = when (this) {
        MOB_ALL -> true
        MOB_HOSTILE -> Mobs.isHostile(type)
        MOB_ANIMAL -> Mobs.isAnimal(type)
        CROP_ALL -> false
    }

    companion object {
        fun of(id: String): Group? = entries.firstOrNull { it.id == id }
    }
}
