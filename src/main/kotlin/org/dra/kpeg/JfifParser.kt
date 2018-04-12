package org.dra.kpeg

import org.dra.kpeg.JpegCodec.Companion.dctInverse
import org.dra.kpeg.util.i
import org.dra.kpeg.util.readNBits
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Created by Derek Alexander
 */

class JfifParser {

    class ParserState {
        val quantTables = Array<JfifQuantizationTable?>(4, {null})
        val dcHuffmanTables = Array<JfifHuffmanTable?>(4, {null})
        val acHuffmanTables = Array<JfifHuffmanTable?>(4, {null})
        var scanInfo: JfifScanTable? = null
        var frameInfo: JfifFrameTable? = null
        var appHeader: AppHeaderTable? = null

        private fun getDecodedTable(table:  Array<JfifHuffmanTable?>, index: Int): HuffmanTool.Companion.ProtoNode {
            if(table[index] == null) {
                throw ArrayIndexOutOfBoundsException()
            }

            val root = table[index]?.calculateTable()?.createTree() ?: throw IllegalStateException()

            return root
        }

        private fun getDecodedDcTable(id: Int): HuffmanTool.Companion.ProtoNode {
            return getDecodedTable(dcHuffmanTables, id)
        }

        private fun getDecodedAcTable(id: Int): HuffmanTool.Companion.ProtoNode {
            return getDecodedTable(acHuffmanTables, id)
        }

        fun getImageBytes(data: List<DataSection>): BlockView<Int> {
            return buildFromOps(bytesToRle(data))
        }

        private fun calculateMcus(): Int {
            val frame = frameInfo
            val scan = scanInfo

            if(frame == null || scan == null) {
                throw IllegalStateException("Trying to calculate MCUs without a frame or scan")
            }

            val maxWidth = frame.channels.fold(0, { sum, cur -> Math.max(sum, cur.horizontalSamplingFactor) })
            val maxHeight = frame.channels.fold(0, { sum, cur -> Math.max(sum, cur.verticalSamplingFactor) })

            val mcuWidth = maxWidth * 8
            val mcuHeight = maxHeight * 8

            val mcusWide = (frame.width + (mcuWidth - 1)) / mcuWidth
            val mcusHigh = (frame.height + (mcuHeight - 1)) / mcuHeight

            val numMcus = mcusWide * mcusHigh

            return numMcus
        }

        @Suppress("NAME_SHADOWING")
        private fun bytesToRle(data: List<DataSection>): List<DataSection> {

            val scan = scanInfo
            val frame = frameInfo
            //val header = appHeader
            if (scan == null || frame == null) {
                //header information is optional?
                throw IllegalStateException("Can't parse a file without scan and frame information")
            }

            val output = mutableListOf<DataSection>()

            for(section in data) {
                if(section !is DataSection.RawImageDataSection) {
                    output.add(section)
                    continue
                }

                val buffer = ByteBuffer.wrap(section.imageData.toByteArray())
                        .order(ByteOrder.BIG_ENDIAN)



                var currentComponent = 0

                val ops = listOf<MutableList<EncodeOp>>(mutableListOf()
                        , mutableListOf()
                        , mutableListOf())

                var component = scan.interleavedComponents[currentComponent]
                var bitIndex = 0
                fun readOp(isDc: Boolean): EncodeOp {
                    val (op, currentBit) = readEncodeOp(buffer, bitIndex, component, isDc)
                    bitIndex = currentBit
                    return op
                }

                val mcusToRead = calculateMcus()
                for (mcuIndex in 0 until mcusToRead) {
                    for (channelIndex in 0 until frame.channels.size) {
                        for (j in 0 until frame.channels.get(currentComponent).verticalSamplingFactor) {
                            for (i in 0 until frame.channels.get(currentComponent).horizontalSamplingFactor) {
                                //read one block per channel, the first item is always a DC coefficient

                                val blockOps = mutableListOf<EncodeOp>()

                                val dcOp = readOp(true)
                                ops[currentComponent].add(dcOp)
                                blockOps.add(dcOp)
                                var zeroSum = 1 //accounting for the DC op
                                do {
                                    val op = readOp(false)
                                    ops[currentComponent].add(op)
                                    blockOps.add(op)
                                    zeroSum += op.leadingZeroes + 1
                                } while (op != EncodeOp.END_BLOCK && zeroSum < 64)

                                var sum = 0
                                for (op in blockOps) {
                                    if (op == EncodeOp.END_BLOCK) {
                                        if (sum < 64) {
                                            sum = 64
                                        }
                                        break
                                    }
                                    sum += op.leadingZeroes + 1
                                }
                                if (sum != 64) {
                                    //println("Wrong number of elements $sum")
                                    throw IllegalStateException("Number of pixels worth of data in this block not equal to 64")
                                }
                            }
                        }

                        currentComponent = (currentComponent + 1) % scan.componentCount
                        component = scan.interleavedComponents[currentComponent]
                    }
                }

                output.add(DataSection.RleDecodedData(ops))
            }

            return output
        }

        private fun buildFromOps(ops: List<DataSection>): BlockView<Int> {
            val scan = scanInfo
            val frame = frameInfo
            if(scan == null || frame == null) {
                throw IllegalStateException("Can't parse a file without scan or frame information")
            }

            val outputColors = decompressChannels(scan, frame, ops)

            return outputColors
        }

        private fun decompressChannels(scan: JfifScanTable, frame: JfifFrameTable, data: List<DataSection>): BlockView<Int> {
            //ops: List<List<JpegCodec.EncodeOp>>

            val maxWidth = frame.channels.fold(0, { sum, cur -> Math.max(sum, cur.horizontalSamplingFactor) })
            val maxHeight = frame.channels.fold(0, { sum, cur -> Math.max(sum, cur.verticalSamplingFactor) })

            val zigzag = JpegCodec.zigzagPattern

            var currentComponent = 0
            //these assume we have a 3 channel image
            val opIndicies = intArrayOf(0, 0, 0)
            val lastDcCoef = intArrayOf(0, 0, 0)

            val backing = IntArray(frame.width * frame.height)
            val output = IntArrayBlockView(backing, frame.width, 0, 0, frame.width, frame.height)

            val numMcus = calculateMcus() //mcusWide * mcusHigh

            val mcus = mutableListOf<List<BlockView<Float>>>()

            var dataIndex = 0
            val firstData = data[dataIndex]
            var ops = if(firstData is DataSection.RleDecodedData) {
                firstData.data
            } else {
                throw IllegalStateException("Not sure what to do without RLE encoded data at the start")
            }
            dataIndex = 1

            fun handleSection(section: DataSection) {
                when(section) {
                    is DataSection.ResetMarker -> {
                        for (i in 0 until 3) {
                            opIndicies[i] = 0
                            lastDcCoef[i] = 0
                        }
                    }
                    is DataSection.RleDecodedData -> {
                        ops = section.data
                    }
                    else -> {
                        throw IllegalStateException("Don't know what to do with data section $section")
                    }
                }
            }

            for(mcuIndex in 0 until numMcus) {

                if (opIndicies[0] >= ops[0].size) { //we should be out of op data...
                    handleSection(data[dataIndex]) //hopefully a reset marker
                    dataIndex++
                    handleSection(data[dataIndex]) //hopefully a RLE section
                    dataIndex++
                }

                val organizedChannels = mutableListOf<BlockView<Float>>()

                for(channelIndex in 0 until frame.channels.size) {
                    //the order of these may need to be reversed
                    val channelOps = ops[channelIndex % scan.componentCount]
                    var opIndex = opIndicies[currentComponent]
                    val blocksHigh = frame.channels.get(currentComponent).verticalSamplingFactor
                    val blocksWide = frame.channels.get(currentComponent).horizontalSamplingFactor
                    val outputBlocks = mutableListOf<BlockView<Float>>()
                    for (j in 0 until blocksHigh) {
                        for (i in 0 until blocksWide) {

                            //read one block per channel, the first item is always a DC coefficient
                            val block = BlockIntDataView(8, 8)//ArrayBlockView(currentRes, frame.width, 0, (i + (j * horizontalFactor)) * 8, 8, 8)
                            val dcOp = channelOps[opIndex]
                            block[0] = dcOp.data + lastDcCoef[currentComponent] //decode DC
                            lastDcCoef[currentComponent] += dcOp.data
                            opIndex++
                            var blockIndex = 1
                            do {
                                if (channelOps.size <= opIndex) {
                                    println("Stop.")
                                }
                                val op = channelOps[opIndex]
                                //println("$blockIndex + ${op.leadingZeroes} done? ${op == JpegCodec.EncodeOp.END_BLOCK}")
                                blockIndex += op.leadingZeroes

                                block[zigzag[blockIndex]] = op.data //decode AC
                                blockIndex++

                                opIndex++
                            } while (op != EncodeOp.END_BLOCK && blockIndex < 64)

                            val quantTable = this.quantTables[frame.channels.get(currentComponent).quantizationTableId]!!.table.map { it.toInt() }.toIntArray()
                            val unZigzagQuant = IntArray(64)
                            for (q in 0 until 64) {
                                unZigzagQuant[zigzag[q]] = quantTable[q]
                            }
                            //dequantize the table
                            val quantizer = Quantizer(unZigzagQuant)
                            val deQuantized = quantizer.deQuantize(block)

                            //iDCT the table
                            //todo: avoid the extra array allocation here
                            val inverted = dctInverse(deQuantized)

                            //shift up by 128 to invert the 128 shift down in encoding... this is handled by dctInverse

                            outputBlocks.add(inverted)
                        }
                    }

                    opIndicies[channelIndex] = opIndex
                    currentComponent = (currentComponent + 1) % scan.componentCount

                    if (blocksWide < maxWidth || blocksHigh < maxHeight) {
                        organizedChannels.add(BilinearBlockView(outputBlocks[0], 16, 16))
                        //organizedChannels.add(SamplingBlockView(outputBlocks[0], maxWidth * 8, maxHeight * 8))
                    } else {
                        organizedChannels.add(CompositeBlockView(outputBlocks, blocksWide * 8, blocksHigh * 8, 8, 8))
                    }
                }

                mcus.add(organizedChannels)
            }

            val mcuWidth = maxWidth * 8
            val mcuHeight = maxHeight * 8
            val mcusWide = (frame.width + (mcuWidth - 1)) / mcuWidth
            val mcusHigh = (frame.height + (mcuHeight - 1)) / mcuHeight

            for(mcuIndex in 0 until numMcus) {
                val organizedChannels = mcus[mcuIndex]
                val xoffset = (mcuIndex % mcusWide) * mcuWidth
                val yoffset = (mcuIndex / mcusWide) * mcuHeight
                var cy = 0
                for (y in yoffset until Math.min(frame.height, yoffset + mcuHeight)) {
                    var cx = 0
                    for (x in xoffset until Math.min(frame.width, xoffset + mcuWidth)) {
                        val lum = organizedChannels[0][cy, cx]
                        val b = organizedChannels[1][cy, cx]
                        val r = organizedChannels[2][cy, cx]
                        val combinedColor = ColorSpace.ycbcrToRgb(lum, b, r)
                        output[y, x] = combinedColor
                        cx++
                    }
                    cy++
                }
            }

            return output
        }

        private fun readEncodeOp(buffer: ByteBuffer, bitIndex: Int, currentComponent: ChannelData, isDc: Boolean): Pair<EncodeOp, Int> {

            val table = (if(isDc) {
                    getDecodedDcTable(currentComponent.dcTableId)
                } else {
                    getDecodedAcTable(currentComponent.acTableId)
                })

            val (value, currentBit) = HuffmanTool.decodeOneItem(buffer, bitIndex, table)

            val valueLength = (value.i) and 0xF

            val leadingZeroes = ((value.i) and 0xF0) ushr 4

            val encodedMagnitude = buffer.readNBits(currentBit, valueLength)
            val magnitude = JpegCodec.Companion.expandDecodeInt(encodedMagnitude, valueLength)

            return EncodeOp(leadingZeroes, magnitude) to currentBit + valueLength
        }


    }

    companion object {
        fun parseChunks(file: File): BlockView<Int> {
            return parseChunks(file.inputStream())
        }

        fun parseChunks(stream: InputStream): BlockView<Int> {
            val (parserState, imageData) = readFileData(stream)

            return parserState.getImageBytes(imageData)
        }

        sealed class DataSection {
            class RawImageDataSection(val imageData: MutableList<Byte>): DataSection()
            class ResetMarker(val id: Int): DataSection()

            //outer list is for channels, inner is item
            class RleDecodedData(val data: List<List<EncodeOp>>): DataSection()
        }

        fun readFileData(stream: InputStream, parserState: ParserState = ParserState()): Pair<ParserState, List<DataSection>> {
            val currentChunk = mutableListOf<Byte>()

            var imageData = mutableListOf<Byte>()
            val dataSections = mutableListOf<DataSection>()

            var data = stream.read()
            var dindex = 0
            outer@ while (data != -1) {
                if (data != 0xFF) {
                    currentChunk.add(data.toByte())
                } else {
                    var next = stream.read()
                    dindex++
                    while(next == 0xFF) {
                        //throwing out the fill byte
                        next = stream.read()
                    }
                    when (next) {
                        0x00 -> { //"stuffed" 0xFF
                            currentChunk.add(data.toByte())
                        }
                        //all of these are non-differential, Huffman codings
                        0xC0 -> { //start of frame (baseline DCT)
                            val frameInfo = readFrame(stream)
                            if(parserState.frameInfo != null) {
                                throw NotYetSupportedException("No images with multiple frames supported")
                            }
                            parserState.frameInfo = frameInfo
                        }
                        0xC1 -> { //start of frame, extended sequential DCT
                            //this _might_ work
                            println("Reading extended sequential DCT, this isn't tested behavior")
                            val frameInfo = readFrame(stream)
                            parserState.frameInfo = frameInfo
                        }
                        0xC2 -> { //start of frame (progressive DCT)
                            throw NotYetSupportedException("Progressive DCT jpegs are not supported")
                        }
                        0xC3 -> { //start of frame, lossless, sequential
                            throw NotYetSupportedException("Lossless jpegs are definitely not supported")
                        }
                        0xC4 -> { //huffman tables
                            val table = readHuffmanTable(stream)
                            //todo: implement error checking with the table definitions
                            if(table.tableType != 0 && table.tableType != 1) {
                                throw IllegalStateException("Huffman table type must be 1 or 0! Is ${table.tableType}")
                            }
                            if(table.isDc()) {
                                parserState.dcHuffmanTables[table.tableSlot] = table
                            } else {
                                parserState.acHuffmanTables[table.tableSlot] = table
                            }
                        }

                        //these are all start of frames for differential, huffman coding
                        in 0xC5..0xC7 -> { //differential sequential DCT
                            //These aren't supported... but somehow this header shows up occasionally in data we can read?
                            //throw NotYetSupportedException("Differential DCTs are not supported")
                        }

                        //various start of frames for non-differential (C8-CB) and differential(CD-CF) arithmetic codings
                        in 0xC8..0xCF -> {
                            throw NotYetSupportedException("Arithmatic coded jpegs are not supported")
                        }
                        //Arithmatic coding conditioning
                        0xCC -> {
                        }

                        0xD8 -> { //start of image
                            //readEntireImage(stream) //do nothing, we already have the data in the currentChunk val
                        }
                        0xDA -> { //start of scan
                            val scanHeader = readScanHeader(stream)
                            parserState.scanInfo = scanHeader

                            //then read data until FFD9
                            var temp = stream.read()
                            readData@ while(stream.available() > 0) {
                                if(temp == 0xFF) {
                                    val lookahead = stream.read()
                                    if(lookahead == 0xD9) {
                                        break
                                    } else {
                                        if(lookahead != 0x00) {
                                            when(lookahead) {
                                                in 0xD0 .. 0xD7 -> {
                                                    throw ResetNotSupportedException("Reset indicator found, they are currently not supported.")


                                                    /*dataSections.add(DataSection.RawImageDataSection(imageData))
                                                    dataSections.add(DataSection.ResetMarker(lookahead - 0xD0))
                                                    //println("found reset marker $lookahead")
                                                    imageData = mutableListOf()

                                                    temp = stream.read()
                                                    continue@readData*/
                                                }
                                                0xDA -> {
                                                    throw MultipleHeadersNotSupportedException("Found second data header when reading file, multiple headers aren't supported")
                                                }
                                                else -> {
                                                    throw NotYetSupportedException("Unexpected header indicator ${Integer.toHexString(lookahead)} while reading image data")
                                                }
                                            }
                                        } else {
                                            //this is added later
                                            //imageData.add(0xFF.toByte())
                                        }
                                    }
                                }
                                imageData.add(temp.toByte())
                                temp = stream.read()
                            }
                        }
                        0xDB -> { //quantization tables
                            val table = readQuantTable(stream)
                            table.nextBits
                            parserState.quantTables[table.identifier] = table
                        }
                        0xDC -> { //define number of lines
                        }
                        0xDD -> {  //restart interval
                            val interval = readRestartInterval(stream)
                            println("Found restart interval, it might be required to do something with this? ${interval.mcuRowsPerInterval}")
                        }
                        0xDE -> { //define hierarchical progression
                        }

                        in 0xD0..0xD7 -> { //Restart
                            // Inserted every r macroblocks, where r is the restart interval set by a DRI marker.
                            // Not used if there was no DRI marker. The low three bits of the marker code cycle in value from 0 to 7.
                            // This exists to reset the current DC value in case the jpeg data becomes corrupted.  It's very rarely used.
                            println("Found out-of-data restart indicator")
                        }

                        in 0xE0..0xEF -> { //Application specific (metadata)
                            when (next) {
                                0xE0 -> {
                                    parserState.appHeader = readAppHeader(stream)
                                }
                            //http://dev.exiv2.org/projects/exiv2/wiki/The_Metadata_in_JPEG_files has some metadata information
                                else -> {
                                    // ???
                                    //println("Unknown metadata of code ${Integer.toHexString(next)}")
                                }
                            }
                        }
                        0xFE -> { //Text comment
                            val comment = readComment(stream)
                            //println("Found comment $comment")
                        }
                        0xD9 -> { //End of image
                            break@outer
                        }
                        else -> {
                            //val readHeader = UnknownHeaderWrapper(stream)
                            //lots of random stuff here, possible that we are getting a thumbnail or something that we aren't parsing
                            //println("Unknown header tag of: ${Integer.toHexString(data)} - ${Integer.toHexString(next)}")// of length ${readHeader.data.length} (${Integer.toHexString(readHeader.data.length)})")
                        }
                    }
                }

                data = stream.read()
                dindex++
            }

            dataSections.add(DataSection.RawImageDataSection(imageData))

            return parserState to dataSections
        }

        private fun readQuantTable(stream: InputStream): JfifQuantizationTable {
            val wrapper = JfifQuantizationWrapper(stream)
            return wrapper.data
        }

        private fun readRestartInterval(stream: InputStream): RestartIntervalTable {
            val wrapper = RestartIntervalWrapper(stream)
            return wrapper.data
        }

        private fun readScanHeader(stream: InputStream): JfifScanTable {
            val wrapper = JfifScanWrapper(stream)

            return wrapper.data
        }

        private fun readHuffmanTable(stream: InputStream): JfifHuffmanTable {
            val wrapper = JfifHuffmanWrapper(stream)
            return wrapper.data
        }

        private fun readFrame(stream: InputStream): JfifFrameTable {
            val wrapper = JfifFrameWrapper(stream)
            return wrapper.data
        }

        private fun readComment(stream: InputStream): String {
            val wrapper = JfifCommentWrapper(stream)
            return wrapper.data.string
        }

        private fun readAppHeader(stream: InputStream): AppHeaderTable {
            val wrapper = JfifHeaderTableWrapper(stream)
            return wrapper.data
        }

        fun readHeader(stream: InputStream): Pair<Int, ByteArray> {
            val lenMsb = stream.read()
            val lenLsb = stream.read()

            if (lenLsb == -1 || lenLsb == -1) {
                throw IllegalStateException("Unexpectedly reached end of stream while reading App0 Header!  Bytes $lenMsb and $lenLsb should be >= 0")
            }

            val headerLength = (lenMsb shl 8) or lenLsb

            if (headerLength < 2) {
                throw IllegalStateException("Impossible App0 header format!  Header length specifies that it is impossibly short ($headerLength bytes)")
            }

            val data = ByteArray(headerLength)
            val lengthOfReadData = stream.read(data, 2, headerLength - 2) //the first two bytes are the header length

            if (lengthOfReadData != headerLength - 2) {
                throw IllegalStateException("Invalid App0 data, read ${lengthOfReadData} bytes, expected to read ${headerLength - 2}")
            }

            data[0] = lenMsb.toByte()
            data[1] = lenLsb.toByte()

            return headerLength to data
        }

        open class NotYetSupportedException(message: String): IllegalStateException(message)
        open class ResetNotSupportedException(message: String): NotYetSupportedException(message)
        open class MultipleHeadersNotSupportedException(message: String): NotYetSupportedException(message)
    }
}
