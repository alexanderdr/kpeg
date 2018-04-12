package org.dra.kpeg

import org.dra.kpeg.util.clamp

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

    class Values(val ulv: Float, val urv: Float, val llv: Float, val lrv: Float)

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