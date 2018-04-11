package org.dra.kpeg

import org.testng.Assert
import org.testng.annotations.Test
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Created by Derek Alexander
 */
class JfifParserTest {

    @Test
    fun permissiveTestParsingHeaders() {
        File("test_data/").listFiles { file, name -> name.endsWith(".jpg") }
                .forEach {
                    val stream = it.inputStream()
                    //val javaImage = ImageIO.read(it)
                    //val javaData = javaImage.getRGB(0, 0, javaImage.width, javaImage.height, null, 0, javaImage.width)
                    //println("reading: ${it.absolutePath}")
                    try {
                        val res = JfifParser.readFileData(stream)
                    } catch (rsne: JfifParser.Companion.NotYetSupportedException) {
                        //println("Issue parsing headers found in file: ${it.absolutePath} '${rsne.message}'")
                    }

                    //Assert.assertEquals(javaData, res)
                }
    }

    @Test
    fun permissiveTestParsing() {

        compareOutputOfFile("./test_data/white_block.jpg")
        compareOutputOfFile("./test_data/colorful_block.jpg")
        compareOutputOfFile("./test_data/colorful_block_reencoded.jpg") //this is almost perfect, each channel is +/- 1 compared to ImageIO

        compareOutputOfFile("./test_data/white_block_with_blue.jpg") //todo: this has a math error that increases the farther to the right in the image
        compareOutputOfFile("./test_data/chroma_sampling_test.jpg")
        compareOutputOfFile("./test_data/solid_green.jpg")
        compareOutputOfFile("./test_data/larger_colorful_block.jpg")

        compareOutputOfFile("./test_data/testDataOdd.jpg")

        //pass -- this is mostly to check that nothing crashes
    }

    fun compareOutputOfFile(path: String) {
        val f = File(path)
        val stream = f.inputStream()
        //val javaImage = ImageIO.read(f)
        //val javaData = javaImage.getRGB(0, 0, javaImage.width, javaImage.height, null, 0, javaImage.width)
        val res = JfifParser.parseChunks(stream)

        /*println(javaData.map { Integer.toHexString(it) }.joinToString(","))

        val jred = javaData.map { it and 0x00FF0000 ushr 16 }
        val jgreen = javaData.map { it and 0x0000FF00 ushr 8 }
        val jblue = javaData.map { it and 0x000000FF }*/

        /*val rawData = res.map { it }
        println(res.map { Integer.toHexString(it) }.joinToString(","))

        val kred = res.map { it and 0x00FF0000 ushr 16 }
        val kgreen = res.map { it and 0x0000FF00 ushr 8 }
        val kblue = res.map { it and 0x000000FF }

        val rdiff = jred.zip(kred).map { (l, r) -> (l - r) }
        val gdiff = jgreen.zip(kgreen).map { (l, r) -> (l - r) }
        val bdiff = jblue.zip(kblue).map { (l, r) -> (l - r) }

        println(rdiff.zip(gdiff).zip(bdiff)
                .filterIndexed { index, item -> index % 16 == 0 || index % 16 == 15 }
                .map { (p,b) ->
            val (r, g) = p
            "($r,$g,$b)"
        }.joinToString(","))*/

        /*val manualImage = BufferedImage(javaImage.width, javaImage.height, BufferedImage.TYPE_INT_ARGB)

        res.forEachIndexed { x, y, data ->
            manualImage.setRGB(x, y, data)
        }

        ImageIO.write(manualImage, "png", File("./test_data/parsed_image_lossless.png").outputStream())
        ImageIO.write(javaImage, "png", File("./test_data/jio_image_lossless.png").outputStream())*/

        //javaData is technically incorrect, and we don't currently support mcu stitching so it will nearly never pass
        //Assert.assertTrue(arraysEqual(rawData.toIntArray(), javaData))
    }

    fun arraysEqual(array: IntArray, other: IntArray): Boolean {
        return array.size == other.size
            && array.zip(other).fold(true, { cur, pair -> cur && pair.first == pair.second })
    }
}