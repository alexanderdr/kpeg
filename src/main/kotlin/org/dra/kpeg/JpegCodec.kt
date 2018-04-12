package org.dra.kpeg

import org.dra.kpeg.util.i
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.Math.PI
import java.lang.Math.cos
import java.util.*

/**
 * Created by Derek Alexander
 */

open class JpegCodec {

    companion object {

        val zigzagPattern = intArrayOf(0, 1, 8, 16, 9, 2, 3, 10,
                                       17, 24, 32, 25, 18, 11, 4, 5,
                                       12, 19, 26, 33, 40, 48, 41, 34,
                                       27, 20, 13, 6, 7, 14, 21, 28,
                                       35, 42, 49, 56, 57, 50, 43, 36,
                                       29, 22, 15, 23, 30, 37, 44, 51,
                                       58, 59, 52, 45, 38, 31, 39, 46,
                                       53, 60, 61, 54, 47, 55, 62, 63)

        //bytes in ARGB format
        @JvmStatic
        fun encodeColors(bytes: ByteArray, width: Int, height: Int, quantizer: Quantizer = Quantizer.DEFAULT): ByteArray {
            /*
         * JPEG encoding, as described by Wikipedia
         *
         * The representation of the colors in the image is converted from RGB to Y′CBCR , consisting of one luma component (Y'), representing brightness, and two chroma components, (CB and CR), representing color. This step is sometimes skipped.
         * The resolution of the chroma data is reduced, usually by a factor of 2 or 3. This reflects the fact that the eye is less sensitive to fine color details than to fine brightness details.
         * The image is split into blocks of 8×8 pixels, and for each block, each of the Y, CB, and CR data undergoes the discrete cosine transform (DCT), which was developed in 1974 by N. Ahmed, T. Natarajan and K. R. Rao; see Citation 1 in discrete cosine transform. A DCT is similar to a Fourier transform in the sense that it produces a kind of spatial frequency spectrum.
         * The amplitudes of the frequency components are quantized. Human vision is much more sensitive to small variations in color or brightness over large areas than to the strength of high-frequency brightness variations. Therefore, the magnitudes of the high-frequency components are stored with a lower accuracy than the low-frequency components. The quality setting of the encoder (for example 50 or 95 on a scale of 0–100 in the Independent JPEG Group's library[20]) affects to what extent the resolution of each frequency component is reduced. If an excessively low quality setting is used, the high-frequency components are discarded altogether.
         * The resulting data for all 8×8 blocks is further compressed with a lossless algorithm, a variant of Huffman encoding.
         */

            val ybrBytes = ColorSpace.rgbToYcbcr(bytes)
            val (yc, b, r) = split3Channels(ybrBytes)
            //subsample(b,2); subsample(r, 2) -- This step is optional, we will skip it for now to get to the org.dra.kpeg.main process

            val lumChannel = encodeChannel(width, height, yc, quantizer)
            val blueChannel = encodeChannel(width, height, b, quantizer)
            val redChannel = encodeChannel(width, height, r, quantizer)

            val allDcLengths = mutableListOf<Byte>()
            val allAcValues = mutableListOf<Byte>()

            val dcs = processDcValues(lumChannel, blueChannel, redChannel)
            allDcLengths.addAll(dcs.map { it.length.toByte() })

            lumChannel.blocks.forEach {
                it.data.forEach {
                    allAcValues.add(it.getBytes().first)
                }
            }

            blueChannel.blocks.forEach {
                it.data.forEach {
                    allAcValues.add(it.getBytes().first)
                }
            }

            redChannel.blocks.forEach {
                it.data.forEach {
                    allAcValues.add(it.getBytes().first)
                }
            }

            val huffDiff = HuffmanTool.buildJpegFriendlyTree(allDcLengths.toByteArray())
            val huff = HuffmanTool.buildJpegFriendlyTree(allAcValues.toByteArray())

            //HuffmanTool.printNodes(huffDiff)
            //HuffmanTool.printNodes(huff)

            val jfif = Jfif()
            jfif.addChunk(JfifApp0(width, height))
            jfif.addChunk(QuantFrame(quantizer.matrix)) //not sure why we should put this before the frame, but it seems to be required?
            jfif.addChunk(JfifFrame(width, height))
            jfif.addChunk(HuffmanFrame(HuffmanTool.HuffmanTable(huffDiff), false))
            jfif.addChunk(HuffmanFrame(HuffmanTool.HuffmanTable(huff), true))
            jfif.addChunk(ScanFrame())
            jfif.addChunk(ThreeChannelDataFrame(lumChannel, blueChannel, redChannel, dcs, huffDiff, huff))

            //HuffmanTool.printNodes(huff)

            val output = ByteArrayOutputStream()
            jfif.writeTo(output)

            return output.toByteArray()
        }

        fun processDcValues(vararg channels: EncodedChannel): List<DcValue> {
            val interwovenValues = mutableListOf<DcValue>()

            val numBlocks = channels[0].blocks.size

            val prevs = IntArray(channels.size, { 0 })

            for (i in 0 until numBlocks) {

                for(ci in 0 until channels.size) {
                    val channel = channels[ci]
                    val previousValue = prevs[ci]

                    val delta = channel.rawDcValues[i] - previousValue
                    val bits = expandEncodeInt(delta)
                    interwovenValues.add(DcValue(bits.length, bits.value))

                    prevs[ci] = channel.rawDcValues[i]
                }
            }

            return interwovenValues
        }

        data class DcValue(val length: Int, val encodedValue: Int)

        fun encodeChannel(width: Int, height: Int, data: ByteArray, quantizer: Quantizer): EncodedChannel {
            //these values grow to 16 if subsampling is involved
            val blockWidth = 8
            val blockHeight = 8

            val blocksAcross = Math.ceil(width.toDouble() / blockWidth).toInt()
            val blocksDown = Math.ceil(height.toDouble() / blockHeight).toInt()

            val lastBlockWidth = if(width % blockWidth == 0) { blockWidth } else { width % blockWidth }
            val lastBlockHeight = if(height % blockHeight == 0) { blockHeight } else { height % blockHeight }

            val rawDcValues = mutableListOf<Int>()

            val allOperations = mutableListOf<List<EncodeOp>>()
            for (y in 0 until blocksDown) {
                val effBlockHeight = if(y != blocksDown - 1) { blockHeight } else { lastBlockHeight }
                for (x in 0 until blocksAcross) {
                    val effBlockWidth = if(x != blocksAcross - 1) { blockWidth } else { lastBlockWidth }
                    val block = if(effBlockHeight < 8 || effBlockWidth < 8) {
                            ExtendedEdgeArrayBlockView(data, width, x * blockWidth, y * blockHeight, effBlockWidth, effBlockHeight)
                        } else {
                            ArrayBlockView(data, width, x * blockWidth, y * blockHeight, effBlockWidth, effBlockHeight)
                        }
                    val dctBlock = discreteCosineTransform(block)
                    val quantized = quantizer.quantize(dctBlock)
                    val encodingOps = if(quantized.width == 8 && quantized.height == 8) {
                        runEncode(quantized)
                    } else {
                        runEncode(VirtualBlockView(quantized, 8, 8))
                    }

                    allOperations.add(encodingOps)
                    rawDcValues.add(quantized[0])
                }
            }

            val blocks = allOperations.map { EncodedBlock(it) }

            return EncodedChannel(blocks, rawDcValues)
        }

        class EncodedChannel(val blocks: List<EncodedBlock>, val rawDcValues: List<Int>)

        data class EncodedBlock(val data: List<EncodeOp>)

        class Jfif {
            private val dataChunks = mutableListOf<JfifBlock>()

            fun writeTo(stream: OutputStream) {
                stream.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
                dataChunks.forEach {
                    it.writeTo(stream)
                }
                stream.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
                stream.flush()
            }

            fun addChunk(block: JfifBlock) {
                dataChunks.add(block)
            }
        }

        interface JfifBlock {
            fun writeTo(stream: OutputStream)
        }

        class JfifApp0(val width: Int, val height: Int): JfifBlock {
            override fun writeTo(stream: OutputStream) {
                //2 bytes: App0 marker
                //~~~~~~~~ (the following is 16 bytes)
                //2 bytes: short containing the length (including these two bytes)
                //5 bytes: identifier string-- JFIF\0 (baseline) or JFXX\0 (extension)
                //2 bytes: JFIF version.  First byte major version, second byte minor version
                //1 bytes: density units, 0: no density information, 1: pixels per inch, 2: pixels per cm
                //2 bytes: horizontal pixel density, must be > 0
                //2 bytes: vertical pixel density, must be > 0
                //1 byte: horizontal pixel count of embedded RGB thumbnail, may be 0
                //1 byte: vertical pixel count of embedded RGB thumbnail, may be 0

                //0xFFE0

                val length = 16

                val output = SequentialByteArrayWriter(ByteArray(length + 2))

                output.writeShort(0xFFE0)
                output.writeShort(length)
                output.writeBytes(*("JFIF" + 0.toChar()).toByteArray())
                output.writeShort(0x0102) //version 1.02

                //Don't know if these are correct, or if they even matter
                output.writeByte(0)
                output.writeShort(width)
                output.writeShort(height)
                //No embedded thumbnail, who even uses this feature?
                output.writeByte(0)
                output.writeByte(0)

                stream.write(output.getArray())
            }
        }

        class SequentialByteArrayWriter(private val array: ByteArray) {
            private var index = 0

            fun getArray(): ByteArray {
                    if(index != array.size) {
                        throw IllegalStateException("Attempt to get byte array without filling it, probable logic error.")
                    }
                    return array
                }

            private var nibbleOffset = 0

            fun writeByte(value: Byte) {
                if (nibbleOffset != 0) {
                    throw IllegalStateException("Cannot correctly write bytes with pending nibbles!")
                }
                array[index] = value
                index++
            }

            fun writeByte(value: Int) {
                writeByte(value.toByte())
            }

            //Note this doesn't work if we aren't already byte aligned!
            fun writeNibble(value: Int) {
                array[index] = (array[index].toInt() or ((value and 0xF) shl (4 - nibbleOffset))).toByte()
                nibbleOffset = (nibbleOffset + 4) % 8
                if (nibbleOffset == 0) {
                    index++
                }
            }

            fun writeNibbles(vararg values: Int) {
                values.forEach {
                    writeNibble(it)
                }
            }

            fun writeShort(value: Short) {
                writeSomeBytes(2, value.toInt())
            }

            fun writeShort(value: Int) {
                writeSomeBytes(2, value)
            }

            fun writePart(subpart: Int, value: Int) {
                val data = (value ushr (subpart * 8)) and 0xFF
                writeByte(data.toByte())
            }

            fun writeBytes(vararg items: Byte) {
                System.arraycopy(items, 0, array, index, items.size)
                index += items.size
            }

            fun writeSomeBytes(number: Int, value: Int) {
                if (number == 4) {
                    writePart(3, value)
                }

                if (number >= 3) {
                    writePart(2, value)
                }

                if (number >= 2) {
                    writePart(1, value)
                }

                if (number >= 1) {
                    writePart(0, value)
                }
            }
        }

        class JfifFrame(val width: Int, val height: Int) : JfifBlock {
            init {
                if (width > 65535 || height > 65535) {
                    throw IllegalArgumentException("Width and height must both be less than 2^16 - 1 because they are stored in an unsigned short")
                }
            }

            //FFC1
            override fun writeTo(stream: OutputStream) {
                //write without packing: 0xFFC1
                //write the length of this 2 bytes
                //write bit precision, 1 byte value: 0x08
                //write the number of lines in the image (2 bytes)
                //write the number of samples per line (columns) (2 bytes)
                //write number of image components in frame 1 byte (used for interleaving the scan components)
                //write out a unique identifier for each image component N x 1 bytes (1 - 255)
                //write out horizontal sampling factor for each image component Nx(4 bits)! (value is probably 1 for our output)
                //write out vertical sampling factor for each image component Nx(4 bits)! (value is probably 1 for our output)
                //quantization table id (between 0 and 3, inclusive) used for each image component Nx1 bytes

                val imageComponentCount = 3

                val length = 8 + imageComponentCount * 3

                val output = SequentialByteArrayWriter(ByteArray(length + 2))

                output.writeShort(0xFFC0)
                output.writeShort(length)
                output.writeByte(0x08)
                output.writeShort(height)
                output.writeShort(width)
                output.writeByte(3) //3 components, YCbCr

                output.writeBytes(1, 0x11, 0) //write Ci, Hi, Vi, Tqi for first component
                output.writeBytes(2, 0x11, 0) //write Ci, Hi, Vi, Tqi for second component
                output.writeBytes(3, 0x11, 0) //write Ci, Hi, Vi, Tqi for third component

                //output.writeBytes(1, 2, 3) //unique ids for our components%%%%%
                //output.writeBytes(0x11, 0x11, 0x11) //1,1 sampling factor
                //output.writeBytes(0, 0, 0) //quantization tables are currently all the same

                stream.write(output.getArray())
            }
        }

        class ScanFrame() : JfifBlock {
            override fun writeTo(stream: OutputStream) {
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

                val numComponents = 3
                val length = 6 + 2 * numComponents

                val output = SequentialByteArrayWriter(ByteArray(length + 2))

                output.writeShort(0xFFDA)
                output.writeShort(length)
                output.writeByte(3) //number of components

                //the components are interwoven
                output.writeBytes(1, 0)
                output.writeBytes(2, 0)
                output.writeBytes(3, 0)

                output.writeByte(0)
                output.writeByte(63)
                output.writeNibble(0)
                output.writeNibble(0)

                stream.write(output.getArray())
            }
        }

        //technically not a real frame, we just have to dump all the encoded data somewhere after we've set everything up
        class ThreeChannelDataFrame(val lumChannel: EncodedChannel,
                                    val blueChannel: EncodedChannel,
                                    val redChannel: EncodedChannel,
                                    val dcValues: List<DcValue>,
                                    val huffDiff: HuffmanTool.Companion.ProtoNode,
                                    val huff: HuffmanTool.Companion.ProtoNode) : JfifBlock {
            override fun writeTo(stream: OutputStream) {
                val channels = listOf(lumChannel, blueChannel, redChannel)

                //we're assuming the channels are all the same size or otherwise there will be problems
                //interleave the data between the channels

                val dcMap = HuffmanTool.generateMap(huffDiff)
                val acMap = HuffmanTool.generateMap(huff)

                var workingSet = 0
                var writtenBits = 0

                fun writeStuffed(data: Int, minByte: Int = 0) {
                    var test = 0xFF shl 24

                    val out = ByteArray(8)
                    var index = 0
                    for(i in 3 downTo minByte) {
                        val res = data and test
                        if (res == test) {
                            //stuff the byte!
                            out[index] = 0xFF.toByte()
                            out[index + 1] = 0x0
                            index += 2
                        } else {
                            out[index] = (res ushr (i * 8)).toByte()
                            index++
                        }
                        test = test ushr 8
                    }

                    stream.write(out, 0, index)
                }

                fun writeBits(data: Int, length: Int) {
                    if(length > 32) {
                        throw IllegalArgumentException("Write bits doesn't support data lengths greater than 32!  Passed $length")
                    }

                    val leftOffset = 32 - (writtenBits + length)
                    var extraBits = 0

                    if(leftOffset >= 0) {
                        workingSet = workingSet or (data shl leftOffset)
                    } else {
                        workingSet = workingSet or (data ushr Math.abs(leftOffset))
                        extraBits = Math.abs(leftOffset)
                    }

                    //This is fine as long as we only write less than 32 bits at a time (which we do)
                    if(extraBits > 0) {
                        val mask = (1 shl extraBits) - 1
                        writeStuffed(workingSet)

                        writtenBits = 0
                        workingSet = 0

                        writeBits(data and mask, extraBits)
                    } else {
                        writtenBits += length
                    }
                }

                fun fillWorkingByte() {
                    //fills the last piece of the current byte in workingSet with 1s, for compliance before we write it out
                    val bitsNeeded = 32 - writtenBits
                    val bits = (1 shl bitsNeeded) - 1
                    workingSet = workingSet or (bits)
                }

                fun writeLeftovers() {
                    //write out the data still in workingSet
                    fillWorkingByte()
                    val minByte = 4 - ((writtenBits + 7) / 8)
                    writeStuffed(workingSet, minByte)
                }

                fun writeOp(op: EncodeOp) {
                    val (lz, encoded) = op.getBytes()
                    val bits = acMap[lz] ?: throw NullPointerException("No byte mapping provided for value ${lz}")
                    val encodedLength = lz.toInt() and 0xF //todo: provide a better way of getting the encoded data length

                    writeBits(bits.value, bits.length)
                    writeBits(encoded.i, encodedLength)
                }

                fun writeDc(dcValue: DcValue) {
                    val huffmanDc = dcMap[dcValue.length.toByte()] ?: throw NullPointerException("No mapping in the DC table for ${dcValue.length.toByte()}")
                    writeBits(huffmanDc.value, huffmanDc.length)
                    writeBits(dcValue.encodedValue, dcValue.length)
                }

                val nextDc = dcValues.iterator()
                for(i in 0 until lumChannel.blocks.size) {
                    for(channel in channels) {
                        //write the DC coefficient
                        /*val dcBits = expandEncodeByte(channel.rawDcValues[i])
                        val huffmanDc = dcMap[dcBits.value] ?: throw NullPointerException("No mapping in the DC table for ${channel.rawDcValues[i]}")*/
                        writeDc(nextDc.next())

                        channel.blocks[i].data.forEach {
                            writeOp(it)
                        }
                    }
                }

                writeLeftovers()
            }
        }

        class HuffmanFrame(val table: HuffmanTool.HuffmanTable, val isAcTable: Boolean = false) : JfifBlock {
            override fun writeTo(stream: OutputStream) {
                //DHT - Huffman marker 0xFFC4
                //Length - 2 + 1 (Th, Tc) + 16 (codes of length i from 1..16) + the number of values
                //Tc - table class, nibble, 0 for DC, 1 for AC
                //Th - table slot, nibble, 0-3, 0 for now
                //Li - the length of each of the 16 rows of the table
                //Vi,j - the data

                val length = 2 + 1 + 16 + table.totalSize
                val output = SequentialByteArrayWriter(ByteArray(length + 2))

                output.writeShort(0xFFC4)
                output.writeShort(length)

                output.writeNibble(if (isAcTable) {
                    1
                } else {
                    0
                })
                output.writeNibble(0)

                for (i in 1 until table.table.size) {
                    output.writeByte(table.table[i]?.size ?: 0)
                }

                for (i in 1 until table.table.size) {
                    val current = table.table[i]
                    if (current == null) {
                        continue
                    }

                    current.forEach {
                        output.writeByte(it)
                    }
                }

                stream.write(output.getArray())
            }
        }

        class QuantFrame(val table: IntArray) : JfifBlock {
            override fun writeTo(stream: OutputStream) {
                //DQT - quant marker 0xFFDB
                //Lq - 2 + 65 (2 + 129 for 16 bit quantization table)
                //Pq - precision, nibble, value: 0 for 8 bit quantization table
                //Tq - Quant table identifier, nibble, always 0 for now
                //Qk - Element k of the table (in zig-zag order)

                val length = 67

                val output = SequentialByteArrayWriter(ByteArray(length + 2))

                output.writeShort(0xFFDB)
                output.writeShort(length)
                output.writeNibble(0)
                output.writeNibble(0)

                //generate table elements in order and write them to our writer
                zigzagPattern.forEach {
                    output.writeByte(table[it].toByte())
                }

                stream.write(output.getArray())
            }
        }

        fun buildTreeForOps(encodingOps: List<EncodeOp>): HuffmanTool.Companion.ProtoNode {
            val huffInput = mutableListOf<Byte>()
            encodingOps.forEach {
                val bytes = it.getBytes()
                huffInput.add(bytes.first)
            }

            val tree = HuffmanTool.buildJpegFriendlyTree(huffInput.toByteArray())

            return tree
        }

        fun discreteCosineTransform(block: BlockView<Byte>): BlockView<Float> {
            //Recenter the data so it's from -128 to 127 instead of 0 to 255
            block.applyInPlace { (it - 128).toByte() }

            //G(u, v) = 1/4 a(u)a(v) * for x, y in 0..7, block[y][x] * cos(((2x + 1) * u * pi) / 16) * cos(((2y + 1) * v * pi) / 16)
            val w = block.width
            val h = block.height
            val output = BlockFloatDataView(w, h)

            for (v in 0 until h) {
                for (u in 0 until w) {
                    var sum = 0.0F
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            sum += block[y, x] *
                                    cos((PI / w) * (x + .5F) * u).toFloat() *
                                    cos((PI / h) * (y + .5F) * v).toFloat()

                            //wikipedia version, for reference
                            //cos(((2 * x + 1) * u * PI) / (2 * w)).toFloat() *
                            //cos(((2 * y + 1) * v * PI) / (2 * h)).toFloat()
                        }
                    }

                    // 1 / sqrt(2)
                    val au = if (u == 0) {
                        1F / 1.414214F
                    } else {
                        1F
                    }
                    val av = if (v == 0) {
                        1F / 1.414214F
                    } else {
                        1F
                    }

                    sum *= au * av * (0.25F)

                    output[v, u] = sum
                }
            }

            return output
        }

        //type III DCT
        fun dctInverse(block: BlockView<Int>): BlockView<Float> {

            val w = block.width
            val h = block.height
            val output = BlockFloatDataView(w, h)

            for (v in 0 until h) {
                for (u in 0 until w) {
                    var sum = 0.0F
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val ax = if (x == 0) {
                                1 / 1.414214F
                            } else {
                                1F
                            }
                            val ay = if (y == 0) {
                                1 / 1.414214F
                            } else {
                                1F
                            }


                            sum += (block[y, x] *
                                    cos(((u + .5F) * x * PI) / 8) *
                                    cos(((v + .5F) * y * PI) / 8)).toFloat() *
                                    ax * ay
                        }
                    }

                    output[v, u] = sum / 4f
                }
            }

            output.applyInPlace { (it + 128) }

            return output
        }

        fun runEncode(data: BlockView<Int>): List<EncodeOp> {
            val pattern = zigzagPattern //zigzagMap[key] ?: throw NullPointerException()
            if (data.width != 8 && data.height != 8) {
                throw IllegalArgumentException("Provided BlockView must be 8x8 in size for the encoding process")
            }

            var leadingZeroes = 0
            val ops = ArrayList<EncodeOp>()

            var lastNonZero = 0

            for (i in 63 downTo 0) {
                if (data[pattern[i]] != 0) {
                    lastNonZero = i
                    break
                }
            }

            //start at 1, 0 should be handled by the DC coefficient
            for(i in 1 until 64) {
                if (i > lastNonZero) { //todo: changed this
                    ops.add(EncodeOp.END_BLOCK)
                    break
                }

                val zzIndex = pattern[i]

                val item = data[zzIndex]

                if (item == 0) {
                    leadingZeroes++
                } else {
                    if(item < -255 || item > 255) {
                        //this would be a problem, can happen occasionally but is somewhat rare
                    }
                    ops.add(EncodeOp(leadingZeroes, item))
                    leadingZeroes = 0
                }

                if (leadingZeroes == 16) {
                    ops.add(EncodeOp(15, 0))
                    leadingZeroes = 0
                }
            }

            return ops
        }

        data class ExpandEncodedByte(val length: Int, val value: Byte)
        data class ExpandEncodedInt(val length: Int, val value: Int)

        fun expandEncodeInt(data: Int): ExpandEncodedInt {
            val length = 32 - Integer.numberOfLeadingZeros(Math.abs(data))

            var d = data and ((1 shl length) - 1)

            if (data < 0) {
                d -= 1
            }

            return ExpandEncodedInt(length, d)
        }

        fun expandDecodeInt(value: Int, length: Int): Int {
            if(value and (0x1 shl (length - 1)) == 0) {
                return (((1 shl length) - 1) * -1) + value
            } else {
                return value
            }
        }

        fun expandEncodeByte(data: Byte): ExpandEncodedByte {
            //0 -1
            //1  1
            //00 -3
            //01 -2
            //10  2
            //11  3
            //000 -7 ...1000 -> 000
            //001 -6
            //010 -5
            //011 -4
            //100  4
            //101  5
            //110  6
            //111  7

            val length = 32 - Integer.numberOfLeadingZeros(Math.abs(data.toInt()))

            var d = data.toInt() and ((1 shl length) - 1)

            if (data < 0) {
                d -= 1
            }

            return ExpandEncodedByte(length, d.toByte())
        }

        //This is a means of producing the zigzag output, it isn't very efficient however
        fun genIndices(width: Int = 8, height: Int = 8): IntArray {
            val current = Point(0, 0)
            val direction = Point(1, 0)

            val output = IntArray(64)

            output[0] = 0
            var index = 1

            while (index < width * height) {
                current += direction

                if (current.y < height && current.x < width) {
                    output[index] = current.y * width + current.x
                    index++
                }

                if (current.y == 0) {
                    if (direction.y == 0) {
                        direction.y = 1
                        direction.x = -1
                    } else {
                        direction.y = 0
                    }
                } else if (current.x == 0) {
                    if (direction.x == 0) {
                        direction.y = -1
                        direction.x = 1
                    } else {
                        direction.x = 0
                    }
                }
            }

            return output
        }

        data class Point(var x: Int, var y: Int) {
            operator fun plusAssign(other: Point) {
                x += other.x
                y += other.y
            }
        }

        fun split3Channels(bytes: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
            if (bytes.size % 3 != 0) {
                throw IllegalArgumentException("To split into 3 channels the byte array must be a multiple of 3")
            }

            val c1 = ByteArray(bytes.size / 3)
            val c2 = ByteArray(bytes.size / 3)
            val c3 = ByteArray(bytes.size / 3)

            val length = bytes.size / 3

            for (index in 0 until length) {
                val view = ThreeView(bytes, index)
                c1[index] = view.c1
                c2[index] = view.c2
                c3[index] = view.c3
            }

            return Triple(c1, c2, c3)
        }
    }

}