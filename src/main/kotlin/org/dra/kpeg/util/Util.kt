package org.dra.kpeg.util

/**
 * Created by Derek Alexander
 */


fun Int.toBinaryString(): String {
    val intermediate = Integer.toBinaryString(this)
    return intermediate.padStart(32,'0')
}

fun Byte.toBinaryString(): String {
    val intermediate = Integer.toBinaryString(this.i)
    return intermediate.padStart(8,'0')
}

fun Float.roundToByte(): Byte {
    return (Math.round(this) and 0xFF).toByte()
}

fun Int.roundToByte(): Byte {
    return (this and 0xFF).toByte()
}

fun Float.roundToInt(): Int {
    return Math.round(this)//(this + .5F).toInt()
}

fun Float.clamp(min: Float, max: Float): Float {
    return Math.max(Math.min(this, max), min)
}

val Byte.i: Int
    get() = this.toInt() and 0xFF