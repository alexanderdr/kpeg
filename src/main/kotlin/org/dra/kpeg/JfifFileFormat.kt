package org.dra.kpeg

import org.dra.kpeg.util.ByteMappedObject
import java.io.InputStream
import java.util.*

/**
 * Created by Dar on 7/30/2017.
 */

class JfifHeaderTableWrapper(source: InputStream): StreamWrapper<AppHeaderTable> (source, { AppHeaderTable(it) })

class AppHeaderTable(backing: ByteArray): ByteMappedObject(backing, 0) {
    //2 bytes: App0 marker (already read)
    //~~~~~~~~
    //2 bytes: short containing the length (including these two bytes)
    //5 bytes: identifier string-- JFIF\0 (baseline) or JFXX\0 (extension)
    //2 bytes: JFIF version.  First byte major version, second byte minor version
    //1 bytes: density units, 0: no density information, 1: pixels per inch, 2: pixels per cm
    //2 bytes: horizontal pixel density, must be > 0
    //2 bytes: vertical pixel density, must be > 0
    //1 byte: horizontal pixel count of embedded RGB thumbnail, may be 0
    //1 byte: vertical pixel count of embedded RGB thumbnail, may be 0
    //more bytes: embedded thumbnail RGB data
    //It's a minor miracle there aren't more buffer overflow attacks against image formats

    val length by bytesAsInt(2)
    val identifierString by byteArray(5)
    val jfifVersion by byteArray(2)
    val densityUnits by bytesAsInt(1)
    val horizontalDensity by bytesAsInt(2)
    val verticalDensity by bytesAsInt(2)
    val thumbnailWidth by bytesAsInt(1)
    val thumbnailHeight by bytesAsInt(1)
    val thumbnailData = byteArray(length - 16) //16 total bytes from the other parts

    /*
    val idString: String = "UNKN" + 0.toChar(),
                     val majorVersion: Int = 0,
                     val minorVersion: Int = 0,
                     val densityCode: DensityType = DensityType.RAW,
                     val horizontalDensity: Int = 1,
                     val verticalDensity: Int = 1,
                     val thumbnailWidth: Int = 0,
                     val thumbnailHeight: Int = 0,
                     val thumbnailData: ByteArray = kotlin.ByteArray(0)
     */
}

class ChannelData(backing: ByteArray, offset: Int): ByteMappedObject(backing, offset) {
    val componentSelector by bytesAsInt(1)
    val dcTableId by bitsAsInt(4)
    val acTableId by bitsAsInt(4)
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

    val length by bytesAsInt(2)
    val componentCount by bytesAsInt(1)
    val interleavedComponents by byteObject<ChannelData>(componentCount, { back, offset -> ChannelData(back, offset) })
    val startOfSelector by bytesAsInt(1)
    val endOfSelector by bytesAsInt(1)
    val bitPositionHigh by bitsAsInt(4)
    val bitPositionLow by bitsAsInt(4)
}

class JfifScanWrapper(source: InputStream): StreamWrapper<JfifScanTable>(source, { JfifScanTable(it) })

class JfifHuffmanTable(backing: ByteArray): ByteMappedObject(backing, 0){
    //DHT - Huffman marker 0xFFC4
    //Length - 2 + 1 (Th, Tc) + 16 (codes of length i from 1..16) + the number of values
    //Tc - table class, nibble, 0 for DC, 1 for AC
    //Th - table slot, nibble, 0-3, 0 for now
    //Li - the length of each of the 16 rows of the table
    //Vi,j - the data

    val length by bytesAsInt(2)
    val tableType by bitsAsInt(4)
    val tableSlot by bitsAsInt(4)
    val lengths by byteArray(16)
    val tableData by byteArray( lengths.sum() )

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
    val length by bytesAsInt(2)
    val someData by byteArray(length - 2)
}

class UnknownHeaderWrapper(source: InputStream): StreamWrapper<UnknownHeaderTable>(source, { UnknownHeaderTable(it) })

class JfifQuantizationTable(backing: ByteArray): ByteMappedObject(backing, 0){
    //DQT - quant marker 0xFFDB
    //Lq - 2 + 65 (2 + 129 for 16 bit quantization table)
    //Pq - precision, nibble, value: 0 for 8 bit quantization table
    //Tq - Quant table identifier, nibble, always 0 for now
    //Qk - Element k of the table (in zig-zag order)

    val length by bytesAsInt(2)
    val precision by bitsAsInt(4)
    val identifier by bitsAsInt(4)
    val table by byteArray(64)
}

class JfifQuantizationWrapper(source: InputStream): StreamWrapper<JfifQuantizationTable>(source, { JfifQuantizationTable(it) })

class FrameChannel(val backing: ByteArray, val offset: Int): ByteMappedObject(backing, offset) {
    val id by bytesAsInt(1)
    val horizontalSamplingFactor by bitsAsInt(4)
    val verticalSamplingFactor by bitsAsInt(4)
    val quantizationTableId by bytesAsInt(1)
}

class JfifFrameTable(backing: ByteArray): ByteMappedObject(backing, 0){
    val length by bytesAsInt(2)
    val bitPrecision by bytesAsInt(1)
    val height by bytesAsInt(2)
    val width by bytesAsInt(2)
    val componentCount by bytesAsInt(1)
    val channels by byteObject<FrameChannel>(componentCount, { back, offset -> FrameChannel(back, offset) })
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

class JfifCommentWrapper(source: InputStream): StreamWrapper<JfifComment> (source, { JfifComment(it) })

class JfifComment(backing: ByteArray): ByteMappedObject(backing, 0) {
    //2 bytes: App0 marker (already read)
    //~~~~~~~~
    //2 bytes: length
    //length - 2 bytes: The string

    val length by bytesAsInt(2)
    private val stringData by byteArray(length - 2)
    val string = String(stringData)
}

class RestartIntervalWrapper(source: InputStream): StreamWrapper<RestartIntervalTable>(source, { RestartIntervalTable(it) })

class RestartIntervalTable(backing: ByteArray): ByteMappedObject(backing, 0) {
    val length by bytesAsInt(2) //always 4
    val mcuRowsPerInterval by bytesAsInt(2)
}