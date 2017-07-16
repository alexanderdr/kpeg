package org.dra.kpeg

import java.io.File
import java.io.InputStream

/**
 * Created by Derek Alexander
 */

class JfifParser {
    companion object {
        fun parseChunks(file: File) {
            parseChunks(file.inputStream())
        }

        fun parseChunks(stream: InputStream) {
            val currentChunk = mutableListOf<Byte>()

            var data = stream.read()
            outer@ while (data != -1) {
                if (data != 0xFF) {
                    currentChunk.add(data.toByte())
                } else {
                    val next = stream.read()
                    when (next) {
                        0x00 -> { //"stuffed" 0xFF
                            currentChunk.add(data.toByte())
                        }
                        //all of these are non-differential, Huffman codings
                        0xC0 -> { //start of frame (progressive DCT)
                        }
                        0xC1 -> { //start of frame, extended sequential DCT
                        }
                        0xC2 -> { //start of frame (baseline DCT)
                        }
                        0xC3 -> { //start of frame, lossless, sequential
                        }
                        0xC4 -> { //huffman tables
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
                        }

                        0xDA -> { //start of scan (image data?)
                        }
                        0xDB -> { //quantization tables
                        }
                        0xDC -> { //define number of lines
                        }
                        0xDD -> {  //restart interval
                            println("Found restart interval")
                        }
                        0xDE -> { //define hierarchical progression
                        }


                        in 0xD0..0xD7 -> { //Restart
                            // Inserted every r macroblocks, where r is the restart interval set by a DRI marker.
                            // Not used if there was no DRI marker. The low three bits of the marker code cycle in value from 0 to 7.
                        }

                        in 0xE0..0xEF -> { //Application specific (metadata)
                            when (next) {
                                0xE0 -> {
                                    readAppHeader(stream)
                                }
                                //http://dev.exiv2.org/projects/exiv2/wiki/The_Metadata_in_JPEG_files has some metadata information
                                else -> {
                                    // ???
                                    println("Unknown metadata of code ${Integer.toHexString(next)}")
                                }
                            }
                        }
                        0xFE -> { //Text comment
                            val comment = readComment(stream)
                            println("Found comment $comment")
                        }
                        0xD9 -> { //End of image
                            break@outer
                        }
                    }
                }

                data = stream.read()
            }

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