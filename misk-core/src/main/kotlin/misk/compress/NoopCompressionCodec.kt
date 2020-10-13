package com.squareup.misk.compress

class NoopCompressionCodec : CompressionCodec {
  override fun encode(input: ByteArray): ByteArray = input
  override fun decode(input: ByteArray): ByteArray = input
}