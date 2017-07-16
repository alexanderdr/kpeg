package org.dra.kpeg

import org.dra.kpeg.util.i
import org.dra.kpeg.util.roundToByte

/**
 * Created by Derek Alexander
 */

class ColorSpace {
    companion object {
        //see also: http://www.itu.int/rec/T-REC-T.871-201105-I/en
        fun rgbToYcbcr(bytes: ByteArray, components: Int = 3): ByteArray {
            if (bytes.size % components != 0) {
                throw IllegalArgumentException("Provided byte array is not a multiple of ${components} in length, cannot be RGB encoded")
            }

            val output = ByteArray((bytes.size / components) * 3)
            val colorSize = bytes.size / components
            for (index in 0 until colorSize) {
                rgbToYcbcr(RgbColor(bytes, index, components), YbrColor(output, index))
            }

            return output
        }

        fun rgbToYcbcr(input: RgbView, output: YbrView) {
            with(input) {
                output.y = (r.i * .299F + g.i * .587F + b.i * .114F).roundToByte()
                output.b = (128 - (r.i * .168736F + g.i * .331264F - b.i * .5F)).roundToByte()
                output.r = (128 - (r.i * -.5F + g.i * .418688F + b.i * .081312F)).roundToByte()
            }
        }

        fun ycbcrToRgb(input: YbrView, output: RgbView) {
            with(input) {
                output.r = (y.i + (r.i - 128) * 1.402F).roundToByte()
                output.g = (y.i - (0.114F * 1.772F * (b.i - 128) + .299F * 1.402F * (r.i - 128)) / 0.587F).roundToByte()
                output.b = (y.i + 1.772F * (b.i - 128)).roundToByte()
            }
        }

        fun ycbcrToRgb(bytes: ByteArray): ByteArray {
            if (bytes.size % 3 != 0) {
                throw IllegalArgumentException("Provided byte array cannot be in YCbCr format, it is not a multiple of 3 in length")
            }

            val output = ByteArray(bytes.size)
            val colorSize = bytes.size / 3
            for (index in 0 until colorSize) {
                ycbcrToRgb(YbrColor(bytes, index), RgbColor(output, index))
            }

            return output
        }
    }
}