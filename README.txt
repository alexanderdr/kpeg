Kpeg is a jpeg encoder written entirely in Kotlin.

It was implemented from scratch based on the the published ITU T81 specification.  It's probably primarily useful as a reference for someone wanting to see what more mathematically-heavy Kotlin looks like, as well as providing a reference for the general process of jpeg encoding.

Note that currently it doesn't support decoding, it's only an encoder.  It also hasn't been run against the paywalled validation data for jpeg encoding (ITU T83) so there may be some subtle flaws in the implementation.