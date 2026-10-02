package com.inmc.drops.track

import com.inmc.drops.Drops
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType

/**
 * **플레이어가 놓은 블록**의 자리 — 청크 PDC `inmcdrops:placed`(정수 배열). 놓은 블록을 다시 캐서 커스텀 드랍을 무한히 뽑는
 * 길(선인장·사탕수수 설치→파괴, 섬세한 손길로 들고 와 다시 놓기)을 막는다.
 *
 * 모든 블록을 적지 않는다 — 큰 건물이 있는 청크가 무거워진다. 적는 것은 [Drops.tables] 가 정한 블록(블록 표의 대상 +
 * 자연 생성 작물)뿐이다. 그래서 표를 **나중에** 만든 블록은 그 전에 놓인 것이 기록이 없어 한 번은 나온다(캐면 끝이라 반복은 안 된다).
 *
 * 청크 PDC 는 청크와 같이 저장된다 — 따로 쓰는 틱이 없다.
 */
class PlacedBlocks {

    private val key = NamespacedKey(Drops.NAMESPACE, "placed")

    fun isPlaced(block: Block): Boolean =
        block.chunk.persistentDataContainer.get(key, PersistentDataType.INTEGER_ARRAY)?.contains(packed(block)) == true

    fun mark(block: Block) {
        val pdc = block.chunk.persistentDataContainer
        val current = pdc.get(key, PersistentDataType.INTEGER_ARRAY) ?: IntArray(0)
        val position = packed(block)
        if (position !in current) pdc.set(key, PersistentDataType.INTEGER_ARRAY, current + position)
    }

    /** 지웠으면 true(있었다). */
    fun unmark(block: Block): Boolean {
        val pdc = block.chunk.persistentDataContainer
        val current = pdc.get(key, PersistentDataType.INTEGER_ARRAY) ?: return false
        val position = packed(block)
        if (position !in current) return false
        val rest = current.filter { it != position }.toIntArray()
        if (rest.isEmpty()) pdc.remove(key) else pdc.set(key, PersistentDataType.INTEGER_ARRAY, rest)
        return true
    }

    private fun packed(block: Block): Int = Packing.pack(block.x, block.y, block.z)
}
