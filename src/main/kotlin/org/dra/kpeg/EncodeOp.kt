package org.dra.kpeg

import org.dra.kpeg.util.roundToByte

class EncodeOp(val leadingZeroes: Int, val data: Int) {
    fun getBytes(): Pair<Byte, Byte> {
        //write the data, in two bytes, (eventually) huffman encoded byte1: (zzzz,dddd) where zzzz are leading zero bits, and dddd is the number of data bits
        //the second byte is purely data, which will not be huffman encoded.

        val (len, encData) = JpegCodec.expandEncodeInt(data)
        val lzByte = ((leadingZeroes shl 4) or len).toByte()
        return lzByte to encData.roundToByte()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as EncodeOp

        if (leadingZeroes != other.leadingZeroes) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int {
        var result = leadingZeroes
        result = 31 * result + data
        return result
    }

    companion object {
        //This should only happen if we already have 15 leading zeroes
        val END_BLOCK = EncodeOp(0, 0)
    }
}