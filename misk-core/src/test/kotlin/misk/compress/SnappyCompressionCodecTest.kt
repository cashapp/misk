package com.squareup.misk.compress

class SnappyCompressionCodecTest : CompressionCodecTest() {
  override val codecUnderTest: CompressionCodec = SnappyCompressionCodec()
}