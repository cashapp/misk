package com.squareup.misk.compress

class DeflateCompressionCodecTest : CompressionCodecTest() {
  override val codecUnderTest = DeflateCompressionCodec()
}