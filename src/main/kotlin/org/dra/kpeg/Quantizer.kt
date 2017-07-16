package org.dra.kpeg

import org.dra.kpeg.util.roundToInt

/**
 * Created by Derek Alexander
 */

open class Quantizer(val matrix: IntArray) {
    object DEFAULT: Quantizer(intArrayOf(8, 16, 16, 18, 24, 32, 32, 48,
            16, 16, 16, 18, 24, 32, 48, 56,
            16, 16, 18, 24, 32, 48, 56, 64,
            16, 18, 24, 32, 48, 56, 64, 64,
            18, 24, 32, 48, 56, 64, 64, 64,
            24, 32, 48, 56, 64, 64, 64, 64,
            32, 48, 56, 64, 64, 64, 64, 64,
            48, 56, 64, 64, 64, 64, 64, 64))

    constructor(generator: (Int, Int) -> Int) : this(IntArray(64).let {
        for(y in 0 until 8) {
            for(x in 0 until 8) {
                it[y * 8 + x] = generator(x, y)
            }
        }
        it
    })

    fun quantize(block: BlockView<Float>): BlockView<Int> {
        val w = block.width
        val h = block.height

        //We need to hold on to our larger values a little bit longer here because we won't necessarily
        //divide by a large enough number at index 0 to fit into a byte (or some of the other indices as well)
        val output = BlockIntDataView(w, h)

        for (x in 0 until w) {
            for (y in 0 until h) {
                if(y != 0 || x != 0) {
                    val res = (block[y, x] / matrix[y * w + x]).roundToInt()
                    if(res > 255 || res < -255) {
                        println("This could be a problem $res outside of the encodable range of -255 to 255")
                    }
                }
                output[y, x] = (block[y, x] / matrix[y * w + x]).roundToInt()
            }
        }

        return output
    }
}