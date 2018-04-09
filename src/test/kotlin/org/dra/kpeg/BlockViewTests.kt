package org.dra.kpeg

import org.testng.annotations.Test

import org.testng.Assert.*

class BlockViewTests {

    @Test
    fun testGetIndex() {
        val baseData = BlockIntDataView(8, 8)
        baseData[0, 0] = 7
        baseData[7, 7] = 61
        baseData[0, 7] = 14
        baseData[7, 0] = 41

        val sampler = SamplingBlockView(baseData, 16, 16)

        assertEquals(sampler[15, 15], 61)
        assertEquals(sampler[15, 14], 61)
        assertEquals(sampler[14, 15], 61)
        assertEquals(sampler[14, 14], 61)

        assertEquals(sampler[0, 0], 7)
        assertEquals(sampler[0, 1], 7)
        assertEquals(sampler[1, 0], 7)
        assertEquals(sampler[1, 1], 7)

        assertEquals(sampler[0, 14], 14)
        assertEquals(sampler[0, 15], 14)
        assertEquals(sampler[1, 14], 14)
        assertEquals(sampler[1, 15], 14)

        assertEquals(sampler[14, 0], 41)
        assertEquals(sampler[14, 1], 41)
        assertEquals(sampler[15, 0], 41)
        assertEquals(sampler[15, 1], 41)
    }

    @Test
    fun `test bilinear filter`() {
        val baseData = BlockFloatDataView(8, 8)
        baseData[0, 0] = 255f
        baseData[0, 1] = 0f
        baseData[0, 6] = 255f
        baseData[0, 7] = 0f

        baseData[7, 7] = 61f
        baseData[7, 0] = 41f

        val bilinear = BilinearBlockView(baseData, 16, 16)
        //val res = bilinear[0, 1]
        //?
        //assertEquals(res, 191.25f, 0.1f)

        assertEquals(bilinear[0,0], 255f, 0.001f)
        assertEquals(bilinear[0, 1], 191.25f, 0.001f)
        assertEquals(bilinear[3,3], 0f, 0.001f)
        //assertEquals(bilinear[0,13], 255f, 0.001f)
    }
}