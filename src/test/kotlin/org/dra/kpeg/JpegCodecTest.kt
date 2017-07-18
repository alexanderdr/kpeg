package org.dra.kpeg

import org.dra.kpeg.util.roundToByte
import org.testng.Assert
import org.testng.annotations.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Created by Derek Alexander
 */

class JpegCodecTest {

    @Test
    fun testDct() {
        //borrowed these from Wikipedia's example
        val bytes = byteArrayOf(52, 55, 61, 66, 70, 61, 64, 73,
                                63, 59, 55, 90, 109, 85, 69, 72,
                                62, 59, 68, 113, 144.toByte(), 104, 66, 73,
                                63, 58, 71, 122, 154.toByte(), 106, 70, 69,
                                67, 61, 68, 104, 126, 88, 68, 70,
                                79, 65, 60, 70, 77, 68, 58, 75,
                                85, 71, 64, 59, 55, 61, 65, 83,
                                87, 79, 69, 68, 65, 76, 78, 94)
        val testBlock = BlockByteDataView(8, 8, bytes)
        val res = JpegCodec.discreteCosineTransform(testBlock)

        //this is the value of the shifted array
        /*val expectedResult = byteArrayOf(
                -76, -73, -67, -62, -58, -67, -64, -55,
                          -65, -69, -73, -38, -19, -43, -59, -56,
                          -66, -69, -60, -15, 16, -24, -62, -55,
                          -65, -70, -57, -6, 26, -22, -58, -59,
                          -61, -67, -60, -24, -2, -40, -60, -58,
                          -49, -63, -68, -58, -51, -60, -70, -53,
                          -43, -57, -64, -69, -73, -67, -63, -45,
                          -41, -49, -59, -60, -63, -52, -50, -34)*/

        //truncated results from the wikipedia page
        val truncatedDct = intArrayOf(-415, -30, -61, 27, 56, -20, -2, 0,
                4, -21, -60, 10, 13, -7, -8, 4,
                -46, 7, 77, -24, -28, 9, 5, -5,
                -48, 12, 34, -14, -10, 6, 1, 1,
                12, -6, -13, -3, -1, 1, -2, 3,
                -7, 2, 2, -5, -2, 0, 4, 1,
                -1, 0, 0, -2, 0, -3, 4, 0,
                0, 0, -1, -4, -1, 0, 0, 1)

        for(i in 0 until 64) {
            Assert.assertEquals(res[i].toInt(), truncatedDct[i], "element $i")
        }
    }

    /*@Test
    fun testZigzagCoding() {
        val temp = org.dra.kpeg.JpegCodec.genIndices()
        println(temp.joinToString(","))
    }*/

    class EqualityPair<T: Any, U: Any>(val first: T, val second: U) {
        override fun equals(other: Any?): Boolean {
            if(other == null || !(other is EqualityPair<*, *>)) {
                return false
            }

            return first == other.first && second == other.second
        }

        override fun hashCode(): Int {
            return super.hashCode()
        }
    }

    fun <T: Any, U: Any> Pair<T, U>.toE() = EqualityPair(this.first, this.second)

    @Test
    fun testEncodeOp() {
        val opNeg7 = JpegCodec.Companion.EncodeOp(0, -7)
        val op7 = JpegCodec.Companion.EncodeOp(0, 7)
        val op1 = JpegCodec.Companion.EncodeOp(0, 1)
        val opNeg1 = JpegCodec.Companion.EncodeOp(0, -1)
        val op17 = JpegCodec.Companion.EncodeOp(0, 17)

        Assert.assertEquals(opNeg7.getBytes(), (3.toByte() to 0.toByte()))
        Assert.assertEquals(op7.getBytes(), 3.toByte() to 7.toByte())
        Assert.assertEquals(op1.getBytes(), 1.toByte() to 1.toByte())
        Assert.assertEquals(opNeg1.getBytes(), 1.toByte() to 0.toByte())
        Assert.assertEquals(op17.getBytes(), 5.toByte() to 17.toByte())
    }

    @Test
    fun testUndersizedDct() {
        val fullSize = byteArrayOf(50, 70, 90, 110, 80, 80, 80, 80,
                                            50, 70, 90, 110, 80, 80, 80, 80,
                                            50, 70, 90, 110, 80, 80, 80, 80,
                                            50, 70, 90, 110, 80, 80, 80, 80,
                                            80, 80, 80, 80, 80, 80, 80, 80,
                                            80, 80, 80, 80, 80, 80, 80, 80,
                                            80, 80, 80, 80, 80, 80, 80, 80,
                                            80, 80, 80, 80, 80, 80, 80, 80)

        val quarterSize = byteArrayOf(50, 70, 90, 110,
                                                50, 70, 90, 110,
                                                50, 70, 90, 110,
                                                50, 70, 90, 110)

        val fullBlock = ArrayBlockView(fullSize, 8, 0, 0, 8, 8)
        val quarterBlock = ExtendedEdgeArrayBlockView(quarterSize, 4, 0, 0, 4, 4)

        val fullDct = JpegCodec.discreteCosineTransform(fullBlock)
        val quarterDct = JpegCodec.discreteCosineTransform(quarterBlock)
    }

    @Test
    fun testEncodeColors() {
        val (width, height, dataBytes) = loadFilenameAsBytes("testDataOdd.jpg")

        val output = JpegCodec.encodeColors(dataBytes, width, height, Quantizer({x, y -> Math.max(4, (x + 1) * (y + 1))}))

        val f = File("test_data/testEncodeColorsOutput.jpg")
        f.delete()
        f.createNewFile()
        f.writeBytes(output)

        //sanity check that we produce a real jpeg that Java can read
        val roundTwo = loadFilenameAsBytes("testEncodeColorsOutput.jpg")

        //This might need to change if any improvements to the encoding process are implemented
        val expectedOutput = loadFilenameAsBytes("testDataRencoded_max_4_xp1_times_yp1.jpg")

        Assert.assertEquals(roundTwo.third, expectedOutput.third)
    }

    @Test
    fun testEncodeWhiteBlock() {
        val (width, height, dataBytes) = loadFilenameAsBytes("white_block.jpg")

        val output = JpegCodec.encodeColors(dataBytes, width, height, Quantizer({x, y -> 4}))

        val outputBytes = ImageIO.read(ByteArrayInputStream(output))

        val imageData = weaveImageChannels(outputBytes)

        //white is an invertible color between YCbCr and RGB so this works
        Assert.assertEquals(imageData.third, ByteArray (64 * 3){ 255.toByte() })
    }

    @Test
    fun roundingWorksAsExpected() {
        val i = -123
        val b = i.roundToByte()

        Assert.assertEquals(b.toInt(), i)

        val ip = 120
        val bp = ip.roundToByte()

        Assert.assertEquals(bp.toInt(), ip)
    }

    @Test
    fun testRunEncodeEmpty() {
        val data = IntArray(64, { 0 })

        val view = IntArrayBlockView(data, 8, 0, 0, 8, 8)

        val encoded = JpegCodec.runEncode(view)

        Assert.assertEquals(1, encoded.size, "Empty encoded block should have one element")
        Assert.assertEquals(true, encoded[0].data == 0 && encoded[0].leadingZeroes == 0, "...And it should be block-finished")
    }

    @Test
    fun testGeneratedHuffmanTreeIsSufficientlyShallow() {
        val (width, height, dataBytes) = loadFilenameAsBytes("testData.jpg")

        //This is now how this will actually be used, but it's sufficient for testing that the rules are followed
        val tree = HuffmanTool.buildJpegFriendlyTree(dataBytes)

        Assert.assertTrue(HuffmanTool.depth(tree) <= 16, "Huffman tree depth must be less than or equal to 16")
    }

    @Test
    fun testZigzagPattern() {
        //each entry here is the order in which that cell is visited (taken from the spec)
        val zigzagTest = intArrayOf(0, 1, 5, 6, 14, 15, 27, 28,
                2, 4, 7, 13, 16, 26, 29, 42,
                3, 8, 12, 17, 25, 30, 41, 43,
                9, 11, 18, 24, 31, 40, 44, 53,
                10, 19, 23, 32, 39, 45, 52, 54,
                20, 22, 33, 38, 46, 51, 55, 60,
                21, 34, 37, 47, 50, 56, 59, 61,
                35, 36, 48, 49, 57, 58, 62, 63)

        val pattern = JpegCodec.zigzagPattern

        val output = IntArray(64)

        for(i in 0 until 64) {
            output[pattern[i]] = i
        }

        Assert.assertEquals(output, zigzagTest)
    }

    fun loadFilenameAsBytes(name: String): Triple<Int, Int, ByteArray> {
        val testImage = ImageIO.read(File("test_data/$name"))
        return weaveImageChannels(testImage)
    }

    fun weaveImageChannels(testImage: BufferedImage): Triple<Int, Int, ByteArray> {
        val width = testImage.width
        val height = testImage.height
        val data = IntArray(width * height)
        testImage.getRGB(0, 0, width, height, data, 0, width)

        val dataBytes = ByteArray(data.size * 3)

        for (i in 0 until data.size) {
            val r = ((data[i] ushr 16) and 0xFF).toByte()
            val g = ((data[i] ushr 8) and 0xFF).toByte()
            val b = ((data[i] ushr 0) and 0xFF).toByte()

            dataBytes[i * 3 + 0] = r
            dataBytes[i * 3 + 1] = g
            dataBytes[i * 3 + 2] = b
        }
        return Triple(width, height, dataBytes)
    }
}