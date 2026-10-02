package com.inmc.drops.catalog

import org.bukkit.Material
import org.bukkit.entity.Animals
import org.bukkit.entity.Enemy
import org.bukkit.entity.EntityType

/**
 * 몹 목록 — 파일이 아니라 **게임에서** 온다. 켤 때 `EntityType` 의 살아 있는 것 전부라서, 서버 버전이 올라 새 몹이 생기면
 * 따로 등록하지 않아도 목록에 보인다(표 파일은 관리자가 고칠 때 생긴다).
 *
 * 플레이어·갑옷 거치대·마네킹은 뺀다 — 살아 있는 개체지만 "사냥"하는 대상이 아니다.
 */
object Mobs {

    private val EXCLUDED = setOf(EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.MANNEQUIN)

    fun include(type: EntityType): Boolean = type != EntityType.UNKNOWN && type.isAlive && type !in EXCLUDED

    /** 이름순이 아니라 게임의 순서 — 클라이언트 언어마다 이름이 달라 이름으로는 정렬할 수 없다. */
    fun all(): List<EntityType> = EntityType.entries.filter(::include)

    fun id(type: EntityType): String = type.key.key

    fun of(id: String): EntityType? = all().firstOrNull { id(it) == id }

    /** `<lang:…>` — 서버는 한글 이름을 모르지만 클라이언트가 자기 언어로 그린다. */
    fun name(type: EntityType): String = "<lang:" + type.translationKey() + ">"

    fun icon(type: EntityType): Material = Material.matchMaterial(type.name + "_SPAWN_EGG") ?: Material.NAME_TAG

    fun isHostile(type: EntityType): Boolean = type.entityClass?.let { Enemy::class.java.isAssignableFrom(it) } == true

    fun isAnimal(type: EntityType): Boolean = type.entityClass?.let { Animals::class.java.isAssignableFrom(it) } == true
}
