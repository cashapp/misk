package com.squareup.misk.compress

class NoopCompressionCodecTest : CompressionCodecTest() {
  override val codecUnderTest: CompressionCodec = NoopCompressionCodec()
}