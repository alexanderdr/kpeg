package org.dra.kpeg.util

import org.testng.Assert
import org.testng.annotations.Test

/**
 * Created by Dar on 7/18/2017.
 */
class ByteMappedObjectTest {
    @Test
    fun testByteMappingWorksWithStaticFields() {
        val backing = ByteArray(18)
        val mapping = object: ByteMappedObject(backing, 0) {
            //2 bytes: App0 marker -- 0xFFE0
            //~~~~~~~~ (the following is 16 bytes)
            //2 bytes: short containing the length (including these two bytes)
            //5 bytes: identifier string-- JFIF\0 (baseline) or JFXX\0 (extension)
            //2 bytes: JFIF version.  First byte major version, second byte minor version
            //1 bytes: density units, 0: no density information, 1: pixels per inch, 2: pixels per cm
            //2 bytes: horizontal pixel density, must be > 0
            //2 bytes: vertical pixel density, must be > 0
            //1 byte: horizontal pixel count of embedded RGB thumbnail, may be 0
            //1 byte: vertical pixel count of embedded RGB thumbnail, may be 0

            var header by bytesAsInt(2)
            var length by bytesAsInt(2)
            var idString by byteArray(5)
            var version by bytesAsInt(2)
            var density by bytesAsInt(1)
            var horizontalDensity by bytesAsInt(2)
            var verticalDensity by bytesAsInt(2)
            var thumbnailWidth by bytesAsInt(1)
            var thumbnailHeight by bytesAsInt(1)

            init {
                length = nextIndex
            }
        }

        mapping.header = 0xFFE0

        Assert.assertEquals(mapping.header, 0xFFE0)
        Assert.assertEquals(backing[0].i and 0xFF, 0xFF)
        Assert.assertEquals(backing[1].i and 0xFF, 0xE0)

        Assert.assertEquals(mapping.length, 18)

        mapping.thumbnailHeight = 15

        Assert.assertEquals(mapping.thumbnailHeight, 15)

        val someData = byteArrayOf('J'.toByte(), 'p'.toByte(), 'e'.toByte(), 'g'.toByte(), 0)

        mapping.idString = someData

        Assert.assertEquals(mapping.idString, someData)
    }

    @Test
    fun testByteMappingWorksWithDynamicSizes() {
        //actual output data from the huffman table generator
        val data = byteArrayOf(-1,-60,0,61,16,0,2,2,1,3,3,2,3,4,5,2,5,3,5,2,0,0,1,3,2,17,18,33,4,19,5,49,34,
                65,50,97,6,20,-127,113,35,-111,-79,-16,-95,66,21,7,51,82,98,114,-47,-78,-31,-63,36,67,99,-110,
                -109,22,81)

        val mapping = object: ByteMappedObject(data, 0) {
            //DHT - Huffman marker 0xFFC4
            //Length - 2 + 1 (Th, Tc) + 16 (codes of length i from 1..16) + the number of values
            //Tc - table class, nibble, 0 for DC, 1 for AC
            //Th - table slot, nibble, 0-3, 0 for now
            //Li - the length of each of the 16 rows of the table
            //Vi,j - the data

            var header by bytesAsInt(2)
            var length by bytesAsInt(2)
            var tableClass by bits(4)
            var tableSlot by bits(4)
            var tableSizes by byteArray(16)
            var leaves by byteArray(length - 19)
        }

        Assert.assertEquals(mapping.leaves.size, data.size - 21)
        Assert.assertEquals(mapping.leaves[mapping.leaves.size-1], 81)

        Assert.assertEquals(mapping.tableClass, 1)
        Assert.assertEquals(mapping.tableSlot, 0)

        mapping.tableClass = 3
        mapping.tableSlot = 13

        Assert.assertEquals(mapping.tableClass, 3)
        Assert.assertEquals(mapping.tableSlot, 13)
    }

    @Test(expectedExceptions = arrayOf(ArrayIndexOutOfBoundsException::class))
    fun testArrayOutOfBounds() {
        val backing = ByteArray(2)
        val mapping = object: ByteMappedObject(backing, 0) {
            var header by bytesAsInt(2)
            var length by bytesAsInt(2)
        }
    }
}