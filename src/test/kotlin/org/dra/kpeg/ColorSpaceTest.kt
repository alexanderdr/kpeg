package org.dra.kpeg

import org.dra.kpeg.util.i
import org.testng.Assert
import org.testng.annotations.Test

/**
 * Created by Derek Alexander
 */
class ColorSpaceTest {
    @Test
    fun `color space transformations`() {
        val argbData = ByteArray(9 * 3)

        //white pixel
        val white = RgbDataColor(0xFF, 0xFF, 0xFF)
        val black = RgbDataColor(0x00, 0x00, 0x00)
        val invertibleRed = RgbDataColor(0xFE, 0x00, 0x00)
        val invertibleGreen = RgbDataColor(0x00, 0xFF, 0x01)
        val invertibleBlue = RgbDataColor(0x00, 0x00, 0xFE)
        val neutralGray = RgbDataColor(0x80, 0x80, 0x80)
        val gammaGray = RgbDataColor(0xC0, 0xC0, 0xC0)
        val mediumBlue = RgbDataColor(0x01, 0x00, 0x80)

        testRgbToYbr(white)
        testRgbToYbr(black)
        testRgbToYbr(invertibleRed)
        testRgbToYbr(invertibleGreen)
        testRgbToYbr(invertibleBlue)
        testRgbToYbr(neutralGray)
        testRgbToYbr(gammaGray)

        // test the actual array backed calculations
        argbData.setRgbPixelColor(0, white)
        argbData.setRgbPixelColor(1, black)
        argbData.setRgbPixelColor(2, invertibleRed)
        argbData.setRgbPixelColor(3, invertibleGreen)
        argbData.setRgbPixelColor(4, invertibleBlue)
        argbData.setRgbPixelColor(5, neutralGray)
        argbData.setRgbPixelColor(6, gammaGray) //gamma-adjusted 50% gray
        argbData.setRgbPixelColor(7, mediumBlue)

        val ycbcrOutput = ColorSpace.rgbToYcbcr(argbData)
        val rgbOutput = ColorSpace.ycbcrToRgb(ycbcrOutput)

        for(index in 0 until 8) {
            Assert.assertEquals(RgbColor(rgbOutput, index), RgbColor(argbData, index, 3), "$index")
        }
    }

    @Test
    fun `exhaustive color space search`() {
        var rgb = RgbDataColor(0, 0, 0)

        val ybrOutput: YbrView = YbrDataColor(0, 0, 0)
        val rgbOutput: RgbView = RgbDataColor(0, 0, 0)

        for(i in 0 until (0x1000000 - 1)) {
            val red = (0xFF0000 and i) ushr 16
            val green = (0xFF00 and i) ushr 8
            val blue = 0xFF and i

            rgb = RgbDataColor(red, green, blue)

            ColorSpace.rgbToYcbcr(rgb, ybrOutput)
            ColorSpace.ycbcrToRgb(ybrOutput, rgbOutput)

            val or = rgbOutput.r.i - rgb.r.i
            val og = rgbOutput.g.i - rgb.g.i
            val ob = rgbOutput.b.i - rgb.b.i
            if(Math.abs(or) > 1 || Math.abs(og) > 1 || Math.abs(ob) > 1) {
                println("$rgb \n produces a delta of $or $og $ob")
            }
            //println(i)
        }

        //Assert.assertEquals(rgbOutput, rgb)
    }

    @Test
    fun `pure blue works`() {
        var rgb = RgbDataColor(0, 0, 0)

        val ybrOutput: YbrView = YbrDataColor(0, 0, 0)
        val rgbOutput: RgbView = RgbDataColor(0, 0, 0)

        rgb = RgbDataColor(0, 0, 255)

        ColorSpace.rgbToYcbcr(rgb, ybrOutput)
        ColorSpace.ycbcrToRgb(ybrOutput, rgbOutput)

        val or = rgbOutput.r.i - rgb.r.i
        val og = rgbOutput.g.i - rgb.g.i
        val ob = rgbOutput.b.i - rgb.b.i
        if(Math.abs(or) > 1 || Math.abs(og) > 1 || Math.abs(ob) > 1) {
            println("$rgb \n produces a delta of $or $og $ob")
        }
        //println(i)

        //Assert.assertEquals(rgbOutput, rgb)
    }

    fun testRgbToYbr(rgb: RgbView) {
        val ybrOutput: YbrView = YbrDataColor(0, 0, 0)
        val rgbOutput: RgbView = RgbDataColor(0, 0, 0)
        ColorSpace.rgbToYcbcr(rgb, ybrOutput)
        ColorSpace.ycbcrToRgb(ybrOutput, rgbOutput)
        Assert.assertEquals(rgbOutput, rgb)
    }

    fun ByteArray.setRgbPixelColor(index: Int, color: RgbDataColor) {
        //this[index * 4 + 0] = 0xFF.toByte()
        this[index * 3 + 0] = color.r
        this[index * 3 + 1] = color.g
        this[index * 3 + 2] = color.b
    }
}