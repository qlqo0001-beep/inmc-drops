package com.inmc.drops.track

/**
 * 청크 안의 블록 자리 하나를 정수 하나로. 커스텀아이템의 후렴초 표시(`block/CustomBlocks.kt` `packed`)와 같은 식이다 —
 * y 는 12비트(−2048~2047), x·z 는 청크 안의 4비트씩.
 */
object Packing {

    fun pack(x: Int, y: Int, z: Int): Int = ((y + 2048) shl 8) or ((x and 15) shl 4) or (z and 15)

    fun localX(packed: Int): Int = (packed shr 4) and 15

    fun localZ(packed: Int): Int = packed and 15

    fun y(packed: Int): Int = (packed shr 8) - 2048
}
