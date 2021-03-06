package org.dra.kpeg

import org.dra.kpeg.util.clamp
import org.dra.kpeg.util.i
import org.dra.kpeg.util.roundToByte
import org.dra.kpeg.util.roundToInt

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
                output.y = Math.min(255f, (r.i * .299F + g.i * .587F + b.i * .114F)).roundToByte()
                output.b = Math.min(255f, (128 - (r.i * .168736F + g.i * .331264F - b.i * .5F))).roundToByte()
                output.r = Math.min(255f, (128 - (r.i * -.5F + g.i * .418688F + b.i * .081312F))).roundToByte()
            }
        }

        //Jpeg XR
        fun rgbToYuv(input: RgbView, output: YuvView) {
            with(input) {
                with(output) {
                    v = (b - r).toByte()
                    u = (g - r - (v + 1) / 2).toByte() //ceil(output.v / 2)
                    y = (g - (u + 1) / 2).toByte() //ceil(output.u / 2)
                }
            }
        }

        fun yuvToRgb(input: YuvView, output: RgbView) {
            TODO("Untested")
            /*with(input) {
                with(output) {
                    g = (y + (u + 1) / 2).toByte()
                    r = (g - u - ((v + 1) / 2)).toByte()
                    b = (v + r).toByte()
                }
            }*/
        }

        fun ycbcrToRgb(input: YbrView, output: RgbView) {
            with(input) {
                //todo: this is likely to have errors due to the int / byte conversions, haven't tested well yet
                output.r = Math.max(0f, Math.min((y.i + 1.402F * (r.i - 128)), 255f)).roundToByte()
                output.g = Math.max(0f, Math.min((y.i - 0.344136F * (b.i - 128) - 0.714136F * (r.i - 128)), 255f)).roundToByte()
                output.b = Math.max(0f, Math.min((y.i + 1.772F * (b.i - 128)), 255f)).roundToByte()
            }
        }

        fun verifyYcbcrToRgb(y: Byte, b: Byte, r: Byte) {
            val output = RgbDataColor(0, 0, 0)
            val rawR = y.i + 1.402 * (r.i - 128)
            val rawG = y.i - 0.344136F * (b.i - 128) - 0.714136F * (r.i - 128)
            val rawB = y.i + 1.772F * (b.i - 128)

            if(rawR < 0 || rawR > 255 || rawG < 0 || rawG > 255 || rawB < 0 || rawB > 255) {
                println("Issue when verifying color decode")
            }
        }

        fun ycbcrToRgb(y: Float, b: Float, r: Float): Int {

            //not sure if clamping is what we should do here...
            val cy = y//clamp(0f, 255f, y)
            val cb = b//clamp(0f, 255f, b)
            val cr = r//clamp(0f, 255f, r)

            val rawR = Math.round((cy + 1.402f * (cr - 128)).clamp(0f, 255f)) and 0xFF
            val rawG = Math.round((cy - 0.344136f * (cb - 128) - 0.714136f * (cr - 128)).clamp(0f, 255f)) and 0xFF
            val rawB = Math.round((cy + 1.772F * (cb - 128)).clamp(0f, 255f)) and 0xFF

            return 0xFF000000.toInt() or (rawR shl 16) or (rawG shl 8) or rawB
        }

        fun ycbcrToRgbRounding(y: Float, b: Float, r: Float): Int {

            //not sure if clamping is what we should do here...
            val cy = Math.round(y)//clamp(0f, 255f, y)
            val cb = Math.round(b)//clamp(0f, 255f, b)
            val cr = r//clamp(0f, 255f, r)

            val rawR = Math.round((cy + 1.402f * (cr - 128)).clamp(0f, 255f)) and 0xFF
            val rawG = Math.round((cy - 0.344136f * (cb - 128) - 0.714136f * (cr - 128)).clamp(0f, 255f)) and 0xFF
            val rawB = Math.round((cy + 1.772F * (cb - 128)).clamp(0f, 255f)) and 0xFF

            return 0xFF000000.toInt() or (rawR shl 16) or (rawG shl 8) or rawB
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