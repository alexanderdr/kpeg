package org.dra.kpeg

import org.dra.kpeg.HuffmanTool.Companion.buildJpegFriendlyTree
import org.dra.kpeg.util.toBinaryString
import org.testng.Assert
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Created by Derek Alexander
 */
class HuffmanToolTest {
    @Test
    fun testStringEncoding() {
        val testString = "The rain in Spain falls mainly in the plain."
        val bytes = testString.toByteArray()
        val node = HuffmanTool.buildTree(bytes)

        val items = testString.length

        val bits = HuffmanTool.encode(testString.toByteArray(), node)

        println(bits.map{it.toBinaryString()}.joinToString(","))

        val encodedInts = intArrayOf(0b00110000011111110011010101001011,
                                                0b10100101110001010001010100101110.toInt(),
                                                0b00111010111011100111011001100010,
                                                0b10010111101111011010010111001111.toInt(),
                                                0b00001111111000011110010100101001,
                                                0b00)
        Assert.assertEquals(bits, encodedInts)

        val data = HuffmanTool.decode(bits, items, node)

        Assert.assertEquals(String(data), testString)
    }

    @Test
    fun testTableTranslation() {
        val testString = "The rain in Spain falls mainly in the plain."
        val bytes = testString.toByteArray()
        val node = HuffmanTool.buildJpegFriendlyTree(bytes)

        val table = HuffmanTool.HuffmanTable(node)

        val reRoot = table.createTree()

        val bits = HuffmanTool.encode(bytes, node)
        val result = HuffmanTool.decode(bits, testString.length, reRoot)

        Assert.assertEquals(String(result), testString)
    }

    @Test
    fun testFold() {
        val testString = "The rain in Spain falls mainly in the plain."
        val bytes = testString.toByteArray()
        val tree = HuffmanTool.buildJpegFriendlyTree(bytes)
        assertEquals(tree.fold(0) { curValue, node -> if(node is HuffmanTool.Companion.LeafNode) { curValue + 1 } else { curValue }}, 17)
    }

    @Test
    fun testDataLoad() {
        val tree = buildJpegFriendlyTree(byteArrayOf(0, 12, 3, 1, 0, 2, 17, 3, 17, 0, 63, 0))
        HuffmanTool.printNodes(tree)
    }

    @Test
    fun testSerialization() {

        val testString = "The rain in Spain falls mainly in the plain."
        val bytes = testString.toByteArray()
        val tree = HuffmanTool.buildJpegFriendlyTree(bytes)

        val output = JpegCodec.Companion.HuffmanFrame(HuffmanTool.HuffmanTable(tree))

        val os = ByteArrayOutputStream()

        output.writeTo(os)

        val outputData = os.toByteArray()
        val stream = ByteArrayInputStream(outputData)

        //read the first two table indicator bytes
        stream.read()
        stream.read()

        val inputFrame = JfifHuffmanWrapper(stream).data
        val parsedTree = inputFrame.calculateTable().createTree()

        assertTrue(HuffmanTool.treesEqual(tree, parsedTree))

    }

}