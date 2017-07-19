package org.dra.kpeg.util

import org.testng.Assert
import org.testng.annotations.Test

/**
 * Created by Dar on 7/18/2017.
 */
class ByteMappedObjectTest {
    @Test
    fun testByteMappingWorks() {
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

            var header by bint(2)
            var length by bint(2)
            var idString by barr(5)
            var version by bint(2)
            var density by bint(1)
            var horizontalDensity by bint(2)
            var verticalDensity by bint(2)
            var thumbnailWidth by bint(1)
            var thumbnailHeight by bint(1)

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

    @Test(expectedExceptions = arrayOf(ArrayIndexOutOfBoundsException::class))
    fun testArrayOutOfBounds() {
        val backing = ByteArray(2)
        val mapping = object: ByteMappedObject(backing, 0) {
            var header by bint(2)
            var length by bint(2)
        }
    }
}