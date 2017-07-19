package org.dra.kpeg.util

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
}

interface ArrayBackedData {
    var nextIndex: Int
    val startIndex: Int
    val data: ByteArray

    fun bint(size: Int): AbdDelegateInt {
        val res = AbdDelegateInt(data, startIndex + nextIndex, size)
        nextIndex += size
        return res
    }

    fun barr(size: Int): AbdDelegateBytes {
        val res = AbdDelegateBytes(data, startIndex + nextIndex, size)
        nextIndex += size
        return res
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