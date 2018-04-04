package org.dra.kpeg.util

import java.nio.ByteBuffer
import java.nio.IntBuffer

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

fun IntArray.getBit(index: Int): Int {
    val element = index / 32
    val item = 31 - (index % 32)
    return this[element] and (1 shl item)
}

fun IntBuffer.getBit(index: Int): Int {
    val element = index / 32
    val item = 31 - (index % 32)
    if((index / 32) >= this.capacity()) {
        throw ArrayIndexOutOfBoundsException("Tried to read bit $index but the buffer is only ${this.capacity() * 32} bits long")
    }
    return this[element] and (1 shl item)
}

fun ByteBuffer.getBitAsOne(index: Int): Int {
    val element = index / 8
    val item = 7 - (index % 8)
    if((index / 8) >= this.capacity()) {
        throw ArrayIndexOutOfBoundsException("Tried to read bit $index but the buffer is only ${this.capacity() * 8} bits long")
    }
    val value = (this[element].toInt() and (1 shl item)) ushr item
    return value
}

fun ByteBuffer.getBit(index: Int): Int {
    val element = index / 8
    val item = 7 - (index % 8)
    if((index / 8) >= this.capacity()) {
        throw ArrayIndexOutOfBoundsException("Tried to read bit $index but the buffer is only ${this.capacity() * 8} bits long")
    }
    return this[element].toInt() and (1 shl item)
}


fun ByteBuffer.isBitOne(index: Int): Boolean {
    val element = index / 8
    val item = 7 - (index % 8)
    if((index / 8) >= this.capacity()) {
        throw ArrayIndexOutOfBoundsException("Tried to read bit $index but the buffer is only ${this.capacity() * 8} bits long")
    }
    return this[element].toInt() and (1 shl item) shr item > 0
}

private fun <T> makeBufferReader(wordWidth: Int) {
}

fun IntBuffer.readNBits(start: Int, length: Int): Int {
    if(length > 32) {
        throw IllegalArgumentException("Can't read more than 32 bits at once")
    }
    //todo: optimize this to get all of the bits at once for each element
    var output = 0
    for(i in 0 until length) {
        output = (output shl 1) or getBit(start + i)
    }

    return output
}

fun ByteBuffer.readNBits(start: Int, length: Int): Int {
    if(length > 32) {
        throw IllegalArgumentException("Can't read more than 32 bits at once")
    }
    //todo: optimize this to get all of the bits at once for each element
    var output = 0
    for(i in 0 until length) {
        output = (output shl 1) or getBitAsOne(start + i) //this might need to be getBit
    }

    return output
}

val Byte.i: Int
    get() = this.toInt() and 0xFF