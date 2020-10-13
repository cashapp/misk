package com.squareup.misk.compress

interface CompressionCodec {
  fun encode(input: ByteArray): ByteArray
  fun decode(input: ByteArray): ByteArray
}