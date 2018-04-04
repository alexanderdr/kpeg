package org.dra.kpeg

import org.testng.annotations.Test

import org.testng.Assert.*

class SamplingBlockViewTest {

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
}