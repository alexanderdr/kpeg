package org.dra.kpeg.util

import kotlin.reflect.KProperty

/**
 * Created by Derek Alexander
 */

class ByteMappedObject(override val startIndex: Int): ArrayBackedData {
    override var nextIndex = 0
}

interface ArrayBackedData {
    var nextIndex: Int
    val startIndex: Int

    fun bint(data: ByteArray, size: Int): AdbDelegate {
        val res = AdbDelegate(data, startIndex + nextIndex, size)
        nextIndex += size
        return res
    }

    class AdbDelegate(val dataSource: ByteArray, val location: Int, val size: Int) {
        operator fun getValue(easyData: ArrayBackedData, property: KProperty<*>): Int {
            var res = dataSource[location].toInt()
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