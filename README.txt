Kpeg is a jpeg encoder written entirely in Kotlin.

It was implemented from scratch based on the the published ITU T81 specification.  It's probably primarily useful as a reference for someone wanting to see what more mathematically-heavy Kotlin looks like, as well as providing a reference for the general process of jpeg encoding.

The encoder only uses 4:4:4 chroma subsampling for the images it produces, so the chroma channels are the same size as the luminance channel.  However it supports decoding jpegs 4:2:0 chroma subsampling, which the overwhelming majority of jpegs are encoded as.
There are a couple of limitations with the decoder:
1) It doesn't support jpegs which require reset markers, and will throw an exception when encountering one.  Of the jpegs I sampled 20-30% fall into the "uses reset marker" category.
2) It does not perform "Suppression of block-to-block discontinuities" as specified in section K.8 of the ITU specification.  This means that the decoder will produce slightly different results along block edges than some decoders (either 8x8 or 16x16 depending on chroma subsampling).
3) It produces a more saturated image than the ImageIO decoder.  Probably due to embedded color spaces, but the external application that's producing the images reads this tool's images as more correct than ImageIO's with respect to lightness of dark colors (see: javaIoColorDifference.png).
4) The inverse DCT and YCbCr -> RGB color converter process rounds differently than other decoders, which leads to a slight (0 - 2 of 0 - 255) value difference in the color output compared to other tested decoders

Note that currently it hasn't been run against the paywalled validation data for jpeg encoding (ITU T83) so there may be some subtle flaws in the implementation.