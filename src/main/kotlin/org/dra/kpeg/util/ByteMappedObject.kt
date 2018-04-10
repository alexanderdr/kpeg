package org.dra.kpeg.util

import java.util.*
import kotlin.reflect.KProperty

/**
 * Created by Derek Alexander
 */

open class ByteMappedObject(override val data: ByteArray, override val startIndex: Int): ArrayBackedData {
    override var nextIndex = 0
        set(v) {
            if(v > data.size) {
                throw ArrayIndexOutOfBoundsException("Total size of backing data $v is too large to fit in an array of length ${data.size}")
            }
            field = v
        }

    override var nextBits = 0
}

interface ArrayBackedData {
    var nextIndex: Int
    var nextBits: Int
    val startIndex: Int
    val data: ByteArray

    fun bytesAsInt(size: Int): AbdDelegateInt {
        if(nextBits != 0) {
            throw IllegalStateException("Byte data which is not fully aligned to the 0th bit is not currently supported")
        }

        val res = AbdDelegateInt(data, startIndex + nextIndex, size)
        nextIndex += size
        return res
    }

    //note this is unsafe for objects which cross byte boundaries
    fun bits(size: Int): AbdDelegateIntBits {
        val res = AbdDelegateIntBits(data, startIndex + nextIndex, nextBits, size)
        nextBits += size
        if(nextBits >= 8) {
            if(nextBits != 8) {
                throw IllegalStateException("Bit fields which cross byte boundaries are not currently supported")
            }
            nextBits = nextBits % 8
            nextIndex++
        }
        return res
    }

    fun byteArray(size: Int): AbdDelegateBytes {
        if(nextBits != 0) {
            throw IllegalStateException("Byte data which is not fully aligned to the 0th bit is not currently supported")
        }

        val res = AbdDelegateBytes(data, startIndex + nextIndex, size)
        nextIndex += size
        return res
    }

    fun <T: ArrayBackedData> byteObject(count: Int, creator: (ByteArray, Int) -> T): AbdDelegateObjArray<T> {
        if(nextBits != 0) {
            throw IllegalStateException("Byte data which is not fully aligned to the 0th bit is not currently supported")
        }

        val res = AbdDelegateObjArray(data, startIndex + nextIndex, count, creator)
        nextIndex += res.totalSize
        return res
    }

    class AbdDelegateObjArray<T: ArrayBackedData>(val dataSource: ByteArray, val location: Int, count: Int, creator: (ByteArray, Int) -> T) {
        var totalSize = 0
        val dataArray: ArrayList<T>
        init {
            // So this initialization is really ugly thanks to type erasure
            // but dataArray will conform to the signature by the time the constructor is finished
            @Suppress("UNCHECKED_CAST")
            dataArray = ArrayList<T>(count)
            for(i in 0 until count){
                val item = creator(dataSource, location + totalSize)
                totalSize += item.nextIndex
                dataArray.add(item)
            }
        }

        operator fun getValue(data: ArrayBackedData, property: KProperty<*>): List<T> {
            return dataArray
        }

        // If users want to manipulate the data here they can reference the sub-objects and change things there
        //operator fun setValue(easyData: ArrayBackedData, property: KProperty<*>, value: ByteArray) {
        //}
    }

    class AbdDelegateBytes(val dataSource: ByteArray, val location: Int, val size: Int) {
        operator fun getValue(easyData: ArrayBackedData, property: KProperty<*>): ByteArray {
            val res = ByteArray(size)
            System.arraycopy(dataSource, location, res, 0, size)
            return res
        }

        operator fun setValue(easyData: ArrayBackedData, property: KProperty<*>, value: ByteArray) {
            if(value.size != size) {
                throw ArrayIndexOutOfBoundsException("Size of the provided array is ${value.size} which must exactly match the set delegate size of $size")
            }
            System.arraycopy(value, 0, dataSource, location, value.size)
        }
    }

    class AbdDelegateIntBits(val dataSource: ByteArray, val location: Int, val bitOffset: Int, val size: Int) {
        operator fun getValue(easyData: ArrayBackedData, property: KProperty<*>): Int {
            val res = dataSource[location].toInt() and 0xFF
            val sizeMask = (1 shl size) - 1
            val offset = 8 - (bitOffset + size)
            val value = (res and (sizeMask shl offset)) ushr offset
            return value
        }

        operator fun setValue(easyData: ArrayBackedData, property: KProperty<*>, value: Int) {
            val sizeMask = (1 shl size) - 1
            val offset = 8 - (bitOffset + size)
            val shiftedMask = sizeMask shl offset
            val output = (value and sizeMask) shl offset

            dataSource[location] = (output or (dataSource[location].toInt() and shiftedMask.inv())).toByte()
        }
    }

    class AbdDelegateInt(val dataSource: ByteArray, val location: Int, val size: Int) {
        operator fun getValue(easyData: ArrayBackedData, property: KProperty<*>): Int {
            var res = dataSource[location].toInt() and 0xFF
            for(i in location + 1 until location + size) {
                res = res shl 8
                res = res or (dataSource[i].i and 0xFF)
            }
            return res
        }

        operator fun setValue(easyData: ArrayBackedData, property: KProperty<*>, value: Int) {
            var scratchValue = value
            for(i in location + size - 1 downTo location) {
                dataSource[i] = (scratchValue and 0xFF).toByte()
                scratchValue = scratchValue ushr 8
            }
        }
    }
}