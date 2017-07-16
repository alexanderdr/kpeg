package org.dra.kpeg

/**
 * Created by Derek Alexander
 */

interface ByteArray3View {
    operator fun component1(): Byte
    operator fun component2(): Byte
    operator fun component3(): Byte
}

interface ByteArray4View: ByteArray3View {
    operator fun component4(): Byte
}

open class ThreeView(val backing: ByteArray, index: Int, backingElementCount: Int = 3): ByteArray3View {
    val calculatedOffset = (index * backingElementCount) + (backingElementCount - 3)

    var c1: Byte
        get() = backing[calculatedOffset + 0]
        set(value) { backing[calculatedOffset + 0] = value }
    var c2: Byte
        get() = backing[calculatedOffset + 1]
        set(value) { backing[calculatedOffset + 1] = value }
    var c3: Byte
        get() = backing[calculatedOffset + 2]
        set(value) { backing[calculatedOffset + 2] = value }

    override fun component1() = c1
    override fun component2() = c2
    override fun component3() = c3

    override fun equals(other: Any?): Boolean {
        if(!(other is ThreeView)) {
            return false
        }

        return c1 == other.c1 && c2 == other.c2 && c3 == other.c3
    }

    override fun hashCode(): Int {
        return (c3.toInt() shl 16) or (c2.toInt() shl 8) or (c1.toInt())
    }

    override fun toString(): String {
        return "c1: $c1, c2: $c2, c3: $c3"
    }
}

class FourView(backing: ByteArray, index: Int): ThreeView(backing, index, 4), ByteArray4View {
    var c4: Byte
        get() = backing[calculatedOffset + 3]
        set(value) { backing[calculatedOffset + 3] = value }

    override fun component1() = c1
    override fun component2() = c2
    override fun component3() = c3
    override fun component4() = c4
}

interface RgbView: ByteArray3View {
    var r: Byte
    var g: Byte
    var b: Byte
}

interface YbrView: ByteArray3View {
    var y: Byte
    var b: Byte
    var r: Byte
}

interface ArgbView: RgbView {
    val a: Byte
}

open class RgbColor(backing: ByteArray, index: Int, backingElementCount: Int = 3): ThreeView(backing, index, backingElementCount), RgbView {
    override var r: Byte
        get() = c1
        set(value) { c1 = value }
    override var g: Byte
        get() = c2
        set(value) { c2 = value }
    override var b: Byte
        get() = c3
        set(value) { c3 = value }

    override fun equals(other: Any?): Boolean {
        if(!(other is RgbView)) {
            return false
        }

        return r == other.r && g == other.g && b == other.b
    }

    override fun hashCode(): Int {
        return (r.toInt() shl 16) or (g.toInt() shl 8) or (b.toInt())
    }

    override fun toString(): String {
        return "r: ${r.toHexString()}, g: ${g.toHexString()}, b: ${b.toHexString()}"
    }
}

open class YbrColor(backing: ByteArray, index: Int, backingElementCount: Int = 3): ThreeView(backing, index, backingElementCount), YbrView {
    override var y: Byte
        get() = c1
        set(value) { c1 = value }
    override var b: Byte
        get() = c2
        set(value) { c2 = value }
    override var r: Byte
        get() = c3
        set(value) { c3 = value }


    override fun toString(): String {
        return "Y: ${y.toHexString()}, Cb: ${b.toHexString()}, Cr: ${r.toHexString()}"
    }
}

open class RgbDataColor(override var r: Byte, override var g: Byte, override var b: Byte): RgbView {
    constructor(r: Int, g: Int, b:Int): this(r.toByte(), g.toByte(), b.toByte())

    override fun component1() = r
    override fun component2() = g
    override fun component3() = b

    override fun equals(other: Any?): Boolean {
        if(!(other is RgbView)) {
            return false
        }

        return r == other.r && g == other.g && b == other.b
    }

    override fun hashCode(): Int {
        return (r.toInt() shl 16) or (g.toInt() shl 8) or (b.toInt())
    }

    override fun toString(): String {
        return "r: ${r.toHexString()}, g: ${g.toHexString()}, b: ${b.toHexString()}"
    }
}

fun Byte.toHexString(): String {
    return Integer.toHexString(this.toInt() and 0xFF)
}

open class YbrDataColor(override var y: Byte, override var b: Byte, override var r: Byte): YbrView {
    constructor(r: Int, g: Int, b:Int): this(r.toByte(), g.toByte(), b.toByte())

    override fun component1() = y
    override fun component2() = b
    override fun component3() = r

    override fun toString(): String {
        return "Y: ${y.toHexString()}, Cb: ${b.toHexString()}, Cr: ${r.toHexString()}"
    }
}

interface BlockView<T> {
    fun applyInPlace(func: (T) -> T) {

        for(y in 0 until height) {
            for (x in 0 until width) {
                this[y, x] = func(this[y, x])
            }
        }
    }

    operator fun get(row: Int, col: Int): T
    operator fun set(row: Int, col: Int, value: T)

    //these are unsafe if the calling function doesn't know the size of the blockview
    operator fun get(rawIndex: Int): T
    operator fun set(rawIndex: Int, value: T)

    val width: Int
    val height: Int
}

open class VirtualBlockView(val view: BlockView<Int>, override val width: Int, override val height: Int): BlockView<Int> {

    override fun get(row: Int, col: Int): Int {
        if(row >= height || col >= width) {
            throw ArrayIndexOutOfBoundsException()
        }

        if(row >= view.height || col >= view.width) {
            return 0
        } else {
            return view[row, col]
        }
    }

    override fun set(row: Int, col: Int, value: Int) {
        if(row >= height || col >= width) {
            throw ArrayIndexOutOfBoundsException()
        }

        if(row >= view.height || col >= view.width) {
            //no op, although in theory we could use a backing array here to fake it if we really wanted
        } else {
            view[row, col] = value
        }
    }

    override fun get(rawIndex: Int): Int {
        //pretend to be a wrapped N x N, if it's in a legal location pass it through
        val x = rawIndex % width
        val y = rawIndex / width

        return this[y, x]
    }

    override fun set(rawIndex: Int, value: Int) {
        val x = rawIndex % width
        val y = rawIndex / width

        this[y, x] = value
    }

}

open class ArrayBlockView(val backing: ByteArray, val arrayWidth: Int, val xIndex: Int, val yIndex: Int, override val width: Int, override val height: Int): BlockView<Byte> {

    init {
        if((yIndex + height - 1) * arrayWidth + (xIndex + width) > backing.size) {
            throw ArrayIndexOutOfBoundsException("(${yIndex} + ${height} - 1) * $arrayWidth + ($xIndex + $width) > ${backing.size}")
        }
    }

    override fun get(rawIndex: Int): Byte {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Byte) {
        //not tested
        backing[rawIndex] = value
    }

    /*override fun applyInPlace(change: Byte) {
        for(y in 0 until height) {
            for (x in 0 until width) {
                backing[getIndex(y, x)] = (backing[getIndex(y, x)].toInt() - change.toInt()).toByte()
            }
        }
    }*/

    fun getIndex(row: Int, column: Int): Int {
        return (row + yIndex) * arrayWidth + (column + xIndex)
    }

    override operator fun get(row: Int, col: Int): Byte {
        return backing[(yIndex + row) * arrayWidth + (xIndex + col)]
    }

    override operator fun set(row: Int, col: Int, value: Byte) {
        backing[getIndex(row, col)] = value
    }
}

//todo: combine this
open class IntArrayBlockView(val backing: IntArray, val arrayWidth: Int, val xIndex: Int, val yIndex: Int, override val width: Int, override val height: Int): BlockView<Int> {

    init {
        if((yIndex + height - 1) * arrayWidth + (xIndex + width) > backing.size) {
            throw ArrayIndexOutOfBoundsException("(${yIndex} + ${height} - 1) * $arrayWidth + ($xIndex + $width) > ${backing.size}")
        }
    }

    override fun get(rawIndex: Int): Int {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Int) {
        //not tested
        backing[rawIndex] = value
    }

    /*override fun applyInPlace(change: Int) {
        for(y in 0 until height) {
            for (x in 0 until width) {
                backing[getIndex(y, x)] = (backing[getIndex(y, x)] - change)
            }
        }
    }*/

    fun getIndex(row: Int, column: Int): Int {
        return (row + yIndex) * arrayWidth + (column + xIndex)
    }

    override operator fun get(row: Int, col: Int): Int {
        return backing[(yIndex + row) * arrayWidth + (xIndex + col)]
    }

    override operator fun set(row: Int, col: Int, value: Int) {
        backing[getIndex(row, col)] = value
    }
}

open class ExtendedEdgeArrayBlockView(val backing: ByteArray,
                                      val arrayWidth: Int,
                                      val xIndex: Int,
                                      val yIndex: Int,
                                      private val realWidth: Int,
                                      private val realHeight: Int,
                                      private val fakeWidth: Int = 8,
                                      private val fakeHeight: Int = 8)
        : BlockView<Byte> {
    override val width: Int
        get() = fakeWidth
    override val height: Int
        get() = fakeHeight

    private fun calculateAverage(): Byte {
        var sum: Int = 0
        for(y in 0 until realHeight) {
            for(x in 0 until realWidth) {
                sum += this[y, x]
            }
        }
        return (sum / (realWidth * realHeight)).toByte()
    }

    override fun applyInPlace(func: (Byte) -> Byte) {
        for(y in 0 until realHeight) {
            for (x in 0 until realWidth) {
                backing[getIndex(y, x)] = func(backing[getIndex(y, x)])
            }
        }
    }

    fun getIndex(row: Int, column: Int): Int {
        return (row + yIndex) * arrayWidth + (column + xIndex)
    }

    override fun get(row: Int, col: Int): Byte {
        if(row >= height || col >= width) {
            throw ArrayIndexOutOfBoundsException()
        }

        val ry = Math.min(row, realHeight - 1)
        val rx = Math.min(col, realWidth - 1)

        return backing[(yIndex + ry) * arrayWidth + (xIndex + rx)]
    }

    override operator fun set(row: Int, col: Int, value: Byte) {
        if(row >= height || col >= width) {
            throw ArrayIndexOutOfBoundsException()
        }


        if(row < realHeight || col < realWidth) {
            backing[getIndex(row, col)] = value
        }
    }

    override fun get(rawIndex: Int): Byte {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Byte) {
        //not tested
        backing[rawIndex] = value
    }
}

//todo: Unfortunately it's not simple to combine these because they all use different array object constructors
//The solution is to create some kind of array view that we can operator on, and have one for each data type we use
open class BlockFloatDataView(override val width: Int, override val height: Int): BlockView<Float> {
    val backing = FloatArray(width * height)

    override fun get(rawIndex: Int): Float {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Float) {
        //not tested
        backing[rawIndex] = value
    }

    /*override fun applyInPlace(change: Float) {
        for(y in 0 until height) {
            for (x in 0 until width) {
                backing[getIndex(y, x)] -= change
            }
        }
    }*/

    fun getIndex(row: Int, col: Int): Int {
        return row * width + col
    }

    override operator fun get(row: Int, col: Int): Float {
        return backing[getIndex(row, col)]
    }

    override operator fun set(row: Int, col: Int, value: Float) {
        backing[getIndex(row, col)] = value
    }
}

open class BlockIntDataView(override val width: Int, override val height: Int): BlockView<Int> {
    val backing = IntArray(width * height)

    override fun get(rawIndex: Int): Int {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Int) {
        //not tested
        backing[rawIndex] = value
    }

    /*override fun applyInPlace(change: Int) {
        for(y in 0 until height) {
            for (x in 0 until width) {
                backing[getIndex(y, x)] -= change
            }
        }
    }*/

    fun getIndex(row: Int, col: Int): Int {
        return row * width + col
    }

    override operator fun get(row: Int, col: Int): Int {
        return backing[getIndex(row, col)]
    }

    override operator fun set(row: Int, col: Int, value: Int) {
        backing[getIndex(row, col)] = value
    }
}

open class BlockByteDataView(override val width: Int, override val height: Int, val backing: ByteArray = ByteArray(width * height)): BlockView<Byte> {

    override fun get(rawIndex: Int): Byte {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Byte) {
        //not tested
        backing[rawIndex] = value
    }

    /*override fun applyInPlace(change: Byte) {
        for(y in 0 until height) {
            for (x in 0 until width) {
                backing[getIndex(y, x)] = (backing[getIndex(y, x)].toInt() - change.toInt()).toByte()
            }
        }
    }*/

    fun getIndex(row: Int, col: Int): Int {
        return row * width + col
    }

    override operator fun get(row: Int, col: Int): Byte {
        return backing[getIndex(row, col)]
    }

    override operator fun set(row: Int, col: Int, value: Byte) {
        backing[getIndex(row, col)] = value
    }
}