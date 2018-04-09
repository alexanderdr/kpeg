package org.dra.kpeg

import org.dra.kpeg.util.clamp

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

interface YuvView: ByteArray3View {
    var y: Byte
    var u: Byte
    var v: Byte
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

    fun <U> map(func: (T) -> U): List<U> {
        val output = mutableListOf<U>()
        for(y in 0 until height) {
            for (x in 0 until width) {
                output.add(func(this[y, x]))
            }
        }
        return output
    }

    fun forEachIndexed(func: (Int, Int, T) -> Unit) {
        for(y in 0 until height) {
            for (x in 0 until width) {
                func(x, y, this[y, x])
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
        return backing[(yIndex * arrayWidth) + rawIndex]
    }

    override fun set(rawIndex: Int, value: Byte) {
        backing[(yIndex * arrayWidth) + rawIndex] = value
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

    fun setAll(otherView: BlockView<Float>) {
        if(width != otherView.width || height != otherView.height) {
            throw IllegalArgumentException("Cannot copy data when the widths and heights don't match")
        }

        for(j in 0 until height) {
            for(i in 0 until width) {
                this[j, i] = otherView[j, i].toByte()
            }
        }
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

open class FloatArrayBlockView(val backing: FloatArray, val arrayWidth: Int, val xIndex: Int, val yIndex: Int, override val width: Int, override val height: Int): BlockView<Float> {

    init {
        if((yIndex + height - 1) * arrayWidth + (xIndex + width) > backing.size) {
            throw ArrayIndexOutOfBoundsException("(${yIndex} + ${height} - 1) * $arrayWidth + ($xIndex + $width) > ${backing.size}")
        }
    }

    override fun get(rawIndex: Int): Float {
        //not tested
        return backing[rawIndex]
    }

    override fun set(rawIndex: Int, value: Float) {
        //not tested
        backing[rawIndex] = value
    }

    fun getIndex(row: Int, column: Int): Int {
        return (row + yIndex) * arrayWidth + (column + xIndex)
    }

    override operator fun get(row: Int, col: Int): Float {
        return backing[(yIndex + row) * arrayWidth + (xIndex + col)]
    }

    override operator fun set(row: Int, col: Int, value: Float) {
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

class SamplingBlockView<T>(val source: BlockView<T>, override val width: Int, override val height: Int): BlockView<T> {

    val relativeWidth = width / source.width
    val relativeHeight = height / source.height

    override fun get(rawIndex: Int): T {
        //not tested
        return source[rawIndex / (relativeWidth * relativeHeight)]
    }

    override fun set(rawIndex: Int, value: T) {
        //not tested
        source[rawIndex / (relativeWidth * relativeHeight)] = value
    }

    fun getIndex(row: Int, col: Int): Int {
        return ((row / relativeHeight) * width) / relativeWidth + col / relativeWidth
    }

    override operator fun get(row: Int, col: Int): T {
        return source[getIndex(row, col)]
    }

    override operator fun set(row: Int, col: Int, value: T) {
        source[getIndex(row, col)] = value
    }
}

class BilinearBlockView(val source: BlockView<Float>, override val width: Int, override val height: Int): BlockView<Float> {

    val relativeWidth = width / source.width
    val relativeHeight = height / source.height

    val xStepSize = 1F / (width)
    val xEdgeOffset = xStepSize / 2

    val yStepSize = 1F / (height)
    val yEdgeOffset = yStepSize / 2

    val xSourceHalfBlock = 1F / (source.width * 2)
    val ySourceHalfBlock = 1F / (source.height * 2)

    override fun get(rawIndex: Int): Float {
        //not tested
        return source[rawIndex / (relativeWidth * relativeHeight)]
    }

    override fun set(rawIndex: Int, value: Float) {
        //not tested
        source[rawIndex / (relativeWidth * relativeHeight)] = value
    }

    fun getIndex(row: Int, col: Int): Int {
        return ((row / relativeHeight) * width) / relativeWidth + col / relativeWidth
    }

    fun sample(y: Float, x: Float): Float {
        val fy = Math.min((source.width - 1.0), Math.floor(y.toDouble() * source.width )).toInt()
        val fx = Math.min((source.width - 1.0), Math.floor(x.toDouble() * source.height)).toInt()
        return source[fy, fx]
    }

    fun getCorners(x: Float, y: Float): Corners {
        val cx = x.clamp(xSourceHalfBlock, 1 - xSourceHalfBlock)
        val cy = y.clamp(ySourceHalfBlock, 1 - ySourceHalfBlock)

        return Corners(cx - xSourceHalfBlock, cy - ySourceHalfBlock,
                       cx + xSourceHalfBlock, cy - ySourceHalfBlock,
                       cx - xSourceHalfBlock, cy + ySourceHalfBlock,
                       cx + xSourceHalfBlock, cy + ySourceHalfBlock,
                       cx, cy)
    }

    class Corners(val ulx: Float, val uly: Float, val urx: Float, val ury: Float,
                  val llx: Float, val lly: Float, val lrx: Float, val lry: Float,
                  val cx: Float, val cy: Float) {

        fun getValues(view: BilinearBlockView): Values {
            with(view) {
                val ulv = sample(uly, ulx)
                val urv = sample(ury, urx)
                val llv = sample(lly, llx)
                val lrv = sample(lry, lrx)

                return Values(ulv, urv, llv, lrv)
            }
        }

        fun getWeights(view: BilinearBlockView): Pair<Float, Float> {
            with(view) {
                val xWeight = 1 - (urx * source.width - Math.floor(urx.toDouble() * source.width))
                val yWeight = 1 - (lly * source.width - Math.floor(lly.toDouble() * source.width))
                //val xWeight = cx * (source.width) - Math.floor(cx.toDouble() * (source.width))
                //val yWeight = cy * (source.height) - Math.floor(cy.toDouble() * (source.height))
                return xWeight.toFloat() to yWeight.toFloat()
            }
        }
    }

    class Values(val ulv: Float, val urv: Float, val llv: Float, val lrv: Float) {

    }

    override operator fun get(row: Int, col: Int): Float {

        val xOffset = (col * xStepSize + xEdgeOffset)
        val yOffset = (row * yStepSize + yEdgeOffset)

        val corners = getCorners(xOffset, yOffset)
        val values = corners.getValues(this)

        val (xWeight, yWeight) = corners.getWeights(this)

        //val xWeight = xOffset * (width - 1) - Math.floor(xOffset.toDouble() * (width - 1))
        //val yWeight = yOffset * (height - 1) - Math.floor(yOffset.toDouble() * (height - 1))

        with(values) {
            val res = (ulv * xWeight + urv * (1 - xWeight)) * yWeight + (llv * xWeight + lrv * (1 - xWeight)) * (1 - yWeight)
            return res
        }
    }

    override operator fun set(row: Int, col: Int, value: Float) {
        IllegalAccessError("Cannot set values in a bilinear view")
    }
}

class CompositeBlockView<T>(val source: List<BlockView<T>>,
                            override val width: Int,
                            override val height: Int,
                            val subWidth: Int,
                            val subHeight: Int): BlockView<T> {

    val blocksWide = width / subWidth
    val blocksHigh = height / subHeight

    override fun get(rawIndex: Int): T {
        //not tested
        val row = rawIndex / height
        val col = rawIndex % height

        return get(row, col)
    }

    override fun set(rawIndex: Int, value: T) {
        //not tested
        val row = rawIndex / height
        val col = rawIndex % height

        return set(row, col, value)
    }

    override operator fun get(row: Int, col: Int): T {
        val blocksOver = col / subWidth
        val blocksDown = row / subHeight

        val subWidth = col % subWidth
        val subHeight = row % subHeight

        return source[blocksDown * blocksWide + blocksOver][subHeight, subWidth]
    }

    override operator fun set(row: Int, col: Int, value: T) {
        val blocksOver = col / subWidth
        val blocksDown = row / subHeight

        val subWidth = col % subWidth
        val subHeight = row % subHeight

        source[blocksDown * blocksWide + blocksOver][subHeight, subWidth] = value
    }
}