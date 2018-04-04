package org.dra.kpeg

import org.dra.kpeg.util.ByteMappedObject
import java.io.InputStream
import java.util.*

/**
 * Created by Dar on 7/30/2017.
 */

class ChannelData(backing: ByteArray, offset: Int): ByteMappedObject(backing, offset) {
    val componentSelector by bint(1)
    val dcTableId by nint(4)
    val acTableId by nint(4)
}

class JfifScanTable(backing: ByteArray): ByteMappedObject(backing, 0) {
    //SOS - (start of scan) marker 0xFFDA
    //Ls - scan header length //6 + 2 * Ns
    //Ns - number of image components in the scan (3)
    //Csj - scan component selector (presumably 1, 2, 3 for our YCbCr)
    //Tdj - DC huffman table selector for component j
    //Taj - AC huffman table selector for component j
    //Ss - start of spectral or predictor selection, the first DCT coefficient in each block in the zig-zag order (0 for us)
    //Se - end of spectral selection, see about, but the end (63)
    //Ah - Successive approximation bit position high, 0?
    //Al - Successive approximation bit position low, 0?

    val length by bint(2)
    val componentCount by bint(1)
    val interleavedComponents by bobj<ChannelData>(componentCount, { back, offset -> ChannelData(back, offset) })
    val startOfSelector by bint(1)
    val endOfSelector by bint(1)
    val bitPositionHigh by nint(4)
    val bitPositionLow by nint(4)
}

class JfifScanWrapper(source: InputStream): StreamWrapper<JfifScanTable>(source, { JfifScanTable(it) })

class JfifHuffmanTable(backing: ByteArray): ByteMappedObject(backing, 0){
    //DHT - Huffman marker 0xFFC4
    //Length - 2 + 1 (Th, Tc) + 16 (codes of length i from 1..16) + the number of values
    //Tc - table class, nibble, 0 for DC, 1 for AC
    //Th - table slot, nibble, 0-3, 0 for now
    //Li - the length of each of the 16 rows of the table
    //Vi,j - the data

    val length by bint(2)
    val tableType by nint(4)
    val tableSlot by nint(4)
    val lengths by barr(16)
    val tableData by barr( lengths.sum() )

    fun calculateTable(): HuffmanTool.HuffmanTable {
        val data = arrayListOf<ArrayList<Byte>?>()
        var tableIndex = 0
        for(index in 0 until lengths.size) {
            val curRow = ArrayList<Byte>()
            data.add(curRow)

            val items = lengths[index]
            for(i in 0 until items) {
                curRow.add(tableData[tableIndex])
                tableIndex++
            }
        }

        return HuffmanTool.HuffmanTable(data)
    }

    fun isDc(): Boolean {
        return tableType == 0
    }
}

class JfifHuffmanWrapper(source: InputStream): StreamWrapper<JfifHuffmanTable>(source, { JfifHuffmanTable(it) })

class UnknownHeaderTable(backing: ByteArray): ByteMappedObject(backing, 0) {
    val length by bint(2)
    val someData by barr(length - 2)
}

class UnknownHeaderWrapper(source: InputStream): StreamWrapper<UnknownHeaderTable>(source, { UnknownHeaderTable(it) })

class JfifQuantizationTable(backing: ByteArray): ByteMappedObject(backing, 0){
    //DQT - quant marker 0xFFDB
    //Lq - 2 + 65 (2 + 129 for 16 bit quantization table)
    //Pq - precision, nibble, value: 0 for 8 bit quantization table
    //Tq - Quant table identifier, nibble, always 0 for now
    //Qk - Element k of the table (in zig-zag order)

    val length by bint(2)
    val precision by nint(4)
    val identifier by nint(4)
    val table by barr(64)
}

class JfifQuantizationWrapper(source: InputStream): StreamWrapper<JfifQuantizationTable>(source, { JfifQuantizationTable(it) })

class FrameChannel(val backing: ByteArray, val offset: Int): ByteMappedObject(backing, offset) {
    val id by bint(1)
    val horizontalSamplingFactor by nint(4)
    val verticalSamplingFactor by nint(4)
    val quantizationTableId by bint(1)
}

class JfifFrameTable(backing: ByteArray): ByteMappedObject(backing, 0){
    val length by bint(2)
    val bitPrecision by bint(1)
    val height by bint(2)
    val width by bint(2)
    val componentCount by bint(1)
    val channels by bobj<FrameChannel>(componentCount, { back, offset -> FrameChannel(back, offset) })
}

class JfifFrameWrapper(source: InputStream): StreamWrapper<JfifFrameTable> (source, { JfifFrameTable(it) })

open class StreamWrapper<T: ByteMappedObject>(source: InputStream, cons: (ByteArray) -> T) {
    val data: T

    init {
        val first = source.read()
        val second = source.read()
        val length = (first shl 8) or second
        val backingArray = ByteArray(length)
        backingArray[0] = first.toByte()
        backingArray[1] = second.toByte()
        source.read(backingArray, 2, length - 2)
        data = cons(backingArray)
    }
}