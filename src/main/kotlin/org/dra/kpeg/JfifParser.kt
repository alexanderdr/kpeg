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
        var appHeader: AppHeader? = null

        var currentDcTable = 0
        var currentAcTable = 0

        //private val dcNodes = Array<HuffmanTool.Companion.ProtoNode?>(4, {null})
        //private val acNodes = Array<HuffmanTool.Companion.ProtoNode?>(4, {null})

        private fun getDecodedTable(table:  Array<JfifHuffmanTable?>, index: Int): HuffmanTool.Companion.ProtoNode {
            if(table[index] == null) {
                throw ArrayIndexOutOfBoundsException()
            }

            val root = table[index]?.calculateTable()?.createTree()

            if(root == null) {
                throw IllegalStateException()
            }

            return root
        }

        private fun getDecodedDcTable(id: Int): HuffmanTool.Companion.ProtoNode {
            return getDecodedTable(dcHuffmanTables, id)
        }

        private fun getDecodedAcTable(id: Int): HuffmanTool.Companion.ProtoNode {
            return getDecodedTable(acHuffmanTables, id)
        }

        fun getImageBytes(data: List<Byte>): BlockView<Int> {
            return buildFromOps(bytesToRle(data))
        }

        fun calculateMcus(): Int {
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

            /*val maxVerticalSampling = frame.channels.fold (0, {l, r -> Math.max(l, r.verticalSamplingFactor)})
            val maxHorizontalSampling = frame.channels.fold (0, {l, r -> Math.max(l, r.horizontalSamplingFactor)})
            return Math.max(1, (frame.width / (8 * maxHorizontalSampling))) * Math.max(1, (frame.height / (8 * maxVerticalSampling))) * scan.componentCount*/

            return numMcus
        }

        @Suppress("NAME_SHADOWING")
        fun bytesToRle(data: List<Byte>): List<List<JpegCodec.EncodeOp>> {
            val buffer = ByteBuffer.wrap(data.toByteArray())
                                   .order(ByteOrder.BIG_ENDIAN)

            val scan = scanInfo
            val frame = frameInfo
            val header = appHeader
            if(scan == null || frame == null || header == null) {
                throw IllegalStateException("Can't parse a file without scan, frame and header information")
            }

            var currentComponent = 0

            val ops = listOf<MutableList<JpegCodec.EncodeOp>>(mutableListOf<JpegCodec.EncodeOp>()
                                                     , mutableListOf<JpegCodec.EncodeOp>()
                                                     , mutableListOf<JpegCodec.EncodeOp>())

            var component = scan.interleavedComponents[currentComponent]
            val bitsToRead = data.size * 8
            var bitIndex = 0
            fun readOp(isDc: Boolean): JpegCodec.EncodeOp {
                val (op, currentBit) = readEncodeOp(buffer, bitIndex, component, isDc)
                bitIndex = currentBit
                //println(">> ${op.leadingZeroes},${op.data} $isDc")
                return op
            }


            var mcuIndex = 0
            //8 might need to vary depending on the particular block
            val mcusToRead = calculateMcus()
            for(mcuIndex in 0 until mcusToRead) {
                for(channelIndex in 0 until frame.channels.size) {
                    for (j in 0 until frame.channels.get(currentComponent).verticalSamplingFactor) {
                        for (i in 0 until frame.channels.get(currentComponent).horizontalSamplingFactor) {
                            //read one block per channel, the first item is always a DC coefficient

                            val blockOps = mutableListOf<JpegCodec.EncodeOp>()

                            val dcOp = readOp(true)
                            ops[currentComponent].add(dcOp)
                            blockOps.add(dcOp)
                            var zeroSum = 1 //accounting for the DC op
                            do {
                                val op = readOp(false)
                                ops[currentComponent].add(op)
                                blockOps.add(op)
                                zeroSum += op.leadingZeroes + 1
                            } while (op != JpegCodec.EncodeOp.END_BLOCK && zeroSum < 64)

                            var sum = 0
                            for (op in blockOps) {
                                if (op == JpegCodec.EncodeOp.END_BLOCK) {
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

            return ops
        }

        private fun buildFromOps(ops: List<List<JpegCodec.EncodeOp>>): BlockView<Int> {
            val scan = scanInfo
            val frame = frameInfo
            if(scan == null || frame == null) {
                throw IllegalStateException("Can't parse a file without scan or frame information")
            }

            val outputColors = decompressChannels(scan, frame, ops)

            return outputColors
        }

        private fun decompressChannels(scan: JfifScanTable, frame: JfifFrameTable, ops: List<List<JpegCodec.EncodeOp>>): BlockView<Int> {

            val maxWidth = frame.channels.fold(0, { sum, cur -> Math.max(sum, cur.horizontalSamplingFactor) })
            val maxHeight = frame.channels.fold(0, { sum, cur -> Math.max(sum, cur.verticalSamplingFactor) })

            val result = frame.channels.fold(mutableListOf<ByteArray>(), { listList, channel ->
                //16 x 8 image:
                //2x2 Y -> 256 bytes
                //1x1 C -> 64 bytes
                val widthRatio = maxWidth / channel.horizontalSamplingFactor
                val heightRatio = maxHeight / channel.verticalSamplingFactor
                val mcuWidth = 8 * widthRatio
                val mcuHeight = 8 * heightRatio
                val roundedWidth = frame.width + (mcuWidth - 1)
                val roundedHeight = frame.height + (mcuHeight - 1)
                val blocksWide = (roundedWidth / mcuWidth) + (roundedWidth / mcuWidth) % channel.horizontalSamplingFactor
                val blocksHigh = (roundedHeight / mcuHeight) + (roundedHeight / mcuHeight) % channel.verticalSamplingFactor

                listList.apply { add(ByteArray( blocksWide * blocksHigh * 8 * 8 )) }
            })

            val zigzag = JpegCodec.zigzagPattern

            val mcusToRead = calculateMcus()
            var currentComponent = 0
            //these assume we have a 3 channel image
            val opIndicies = intArrayOf(0, 0, 0)
            val lastDcCoef = intArrayOf(0, 0, 0)

            val backing = IntArray(frame.width * frame.height)
            val output = IntArrayBlockView(backing, frame.width, 0, 0, frame.width, frame.height)

            val mcuWidth = maxWidth * 8
            val mcuHeight = maxHeight * 8

            val mcusWide = (frame.width + (mcuWidth - 1)) / mcuWidth
            val mcusHigh = (frame.height + (mcuHeight - 1)) / mcuHeight

            val numMcus = calculateMcus() //mcusWide * mcusHigh

            val mcus = mutableListOf<List<BlockView<Float>>>()

            for(mcuIndex in 0 until numMcus) {

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

                            println("$mcuIndex - $channelIndex - $j - $i")

                            val currentRes = result[channelIndex % scan.componentCount]
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
                            } while (op != JpegCodec.EncodeOp.END_BLOCK && blockIndex < 64)

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

        private fun SamplingBlockView<Float>.upsample() {
            val output = BlockFloatDataView(width, height)
            for(y in 1 until height step 2) {
                for(x in 1 until width step 2) {
                }
            }
        }

        private fun readEncodeOp(buffer: ByteBuffer, bitIndex: Int, currentComponent: ChannelData, isDc: Boolean): Pair<JpegCodec.EncodeOp, Int> {

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

            //println((currentBit + valueLength) - bitIndex)

            return JpegCodec.EncodeOp(leadingZeroes, magnitude) to currentBit + valueLength
        }


    }

    companion object {
        fun parseChunks(file: File): BlockView<Int> {
            return parseChunks(file.inputStream())
        }

        fun parseChunks(stream: InputStream): BlockView<Int> {
            val currentChunk = mutableListOf<Byte>()

            val parserState = ParserState()

            /*while(stream.available() > 0) {
                val temp = stream.read()
                if(temp == 0xFF && stream.read() == 0x00) {
                    println("Found stuffed byte")
                }
            }
            stream.reset()*/

            val imageData = mutableListOf<Byte>()

            var data = stream.read()
            var dindex = 0
            outer@ while (data != -1) {
                if (data != 0xFF) {
                    currentChunk.add(data.toByte())
                } else {
                    var next = stream.read()
                    dindex++
                    while(next == 0xFF) {
                        println("Throwing out FF fill byte")
                        next = stream.read()
                    }
                    when (next) {
                        0x00 -> { //"stuffed" 0xFF
                            currentChunk.add(data.toByte())
                        }
                        //all of these are non-differential, Huffman codings
                        0xC0 -> { //start of frame (progressive DCT)
                            val frameInfo = readFrame(stream)
                            parserState.frameInfo = frameInfo
                        }
                        0xC1 -> { //start of frame, extended sequential DCT
                        }
                        0xC2 -> { //start of frame (baseline DCT)
                        }
                        0xC3 -> { //start of frame, lossless, sequential
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
                        0xC5 -> { //differential sequential DCT
                        }
                        0xC6 -> { //differential progressive DCT
                        }
                        0xC7 -> { //differential lossless, sequential
                        }

                        //various start of frames for non-differential (C8-CB) and differential(CD-CF) arithmetic codings
                        in 0xC8..0xCF -> {
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
                            while(stream.available() > 0) {
                                if(temp == 0xFF) {
                                    val lookahead = stream.read()
                                    if(lookahead == 0xD9) {
                                        break
                                    } else {
                                        if(lookahead != 0x00) {
                                            throw IllegalArgumentException("Unexpected header indicator ${Integer.toHexString(lookahead)} while reading image data")
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
                            println("Found restart interval, it might be required to parse this?")
                        }
                        0xDE -> { //define hierarchical progression
                        }


                        in 0xD0..0xD7 -> { //Restart
                            // Inserted every r macroblocks, where r is the restart interval set by a DRI marker.
                            // Not used if there was no DRI marker. The low three bits of the marker code cycle in value from 0 to 7.
                            // This exists to reset the current DC value in case the jpeg data becomes corrupted.  It's very rarely used.
                        }

                        in 0xE0..0xEF -> { //Application specific (metadata)
                            when (next) {
                                0xE0 -> {
                                    parserState.appHeader = readAppHeader(stream)
                                }
                                //http://dev.exiv2.org/projects/exiv2/wiki/The_Metadata_in_JPEG_files has some metadata information
                                else -> {
                                    // ???
                                    println("Unknown metadata of code ${Integer.toHexString(next)}")
                                }
                            }
                        }
                        //in 0xF0..0xFD -> { //reserved JPEG extensions
                        //    println()
                        //}
                        0xFE -> { //Text comment
                            val comment = readComment(stream)
                            println("Found comment $comment")
                        }
                        0xD9 -> { //End of image
                            break@outer
                        }
                        else -> {
                            //val readHeader = UnknownHeaderWrapper(stream)
                            //lots of random stuff here, possible that we are getting a thumbnail or something that we aren't parsing
                            println("Unknown header tag of: ${Integer.toHexString(data)} - ${Integer.toHexString(next)}")// of length ${readHeader.data.length} (${Integer.toHexString(readHeader.data.length)})")
                        }
                    }
                }

                data = stream.read()
                dindex++
            }

            //todo: this should use the scan header rather than the random bytes
            //return parserState.getImageBytes(imageData).byteChannelsToIntChannels()
            return parserState.getImageBytes(imageData)
        }

        private fun readQuantTable(stream: InputStream): JfifQuantizationTable {
            val wrapper = JfifQuantizationWrapper(stream)
            return wrapper.data
        }

        private fun readScanHeader(stream: InputStream): JfifScanTable {
            val wrapper = JfifScanWrapper(stream)

            return wrapper.data
        }

        private fun readEntireImage(stream: InputStream) {
            //This is basically irrelevant because we're already going to read the entire file so the 0xFFD8/FFD9 header/footer aren't useful
        }

        private fun readHuffmanTable(stream: InputStream): JfifHuffmanTable {
            val wrapper = JfifHuffmanWrapper(stream)
            return wrapper.data
        }

        private fun readFrame(stream: InputStream): JfifFrameTable {
            val wrapper = JfifFrameWrapper(stream)
            return wrapper.data
        }

        fun readComment(stream: InputStream): String {
            val (headerLength, data) = readHeader(stream)

            return String(data, 2, headerLength - 2)
        }

        fun readAppHeader(stream: InputStream): AppHeader {
           val (headerLength, data) = readHeader(stream)

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

            val identifierString = String(data, 2, 5)

            if (identifierString != ("JFIF" + 0.toChar())) {
                //no don't know how to parse this yet
                return AppHeader()
            }

            val majorVersion = data[7]
            val minorVersion = data[8]

            val densityCode = data[9]

            val horizontalDensity = (data[10].i shl 8) or data[11].i
            val verticalDensity = (data[12].i shl 8) or data[13].i

            val thumbnailWidth = data[14]
            val thumbnailHeight = data[15]

            val rawDataLength = headerLength - 16
            val rawData = ByteArray(rawDataLength)

            if (rawDataLength > 0) {
                System.arraycopy(data, 17, rawData, 0, rawDataLength)
            }

            return AppHeader(identifierString,
                    majorVersion.toInt(),
                    minorVersion.toInt(),
                    DensityType.from(densityCode),
                    horizontalDensity,
                    verticalDensity,
                    thumbnailWidth.toInt(),
                    thumbnailHeight.toInt(),
                    rawData)
        }

        enum class DensityType {
            RAW,
            PPI,
            PPCM;

            companion object {
                fun from(data: Byte): DensityType {
                    return when (data.i) {
                        0 -> RAW
                        1 -> PPI
                        2 -> PPCM
                        else -> RAW
                    }
                }
            }
        }

        data class AppHeader(val idString: String = "UNKN" + 0.toChar(),
                             val majorVersion: Int = 0,
                             val minorVersion: Int = 0,
                             val densityCode: DensityType = DensityType.RAW,
                             val horizontalDensity: Int = 1,
                             val verticalDensity: Int = 1,
                             val thumbnailWidth: Int = 0,
                             val thumbnailHeight: Int = 0,
                             val thumbnailData: ByteArray = kotlin.ByteArray(0)) {
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

        val Byte.i: Int
            get() = this.toInt() and 0xFF
    }
}

private fun ByteArray.byteChannelsToIntChannels(): IntArray {
    if(this.size % 3 != 0) {
        throw IllegalArgumentException("Can only perform this transformation with a byte array that is a multiple of 3 in size")
    }

    val output = IntArray(this.size / 3)
    for(i in 0 until this.size step 3) {
        //r, g, b, A
        output[i / 3] = (this[i].i shl 16) or (this[i + 1].i shl 8) or (this[i + 2].i shl 0) or 0xFF000000.toInt()
    }

    return output
}
