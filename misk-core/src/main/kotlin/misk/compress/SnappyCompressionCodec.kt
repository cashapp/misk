package com.squareup.misk.compress

import org.xerial.snappy.Snappy
import java.io.IOException

/**
 * A {@link CompressionCodec} implementation for the snappy compression format.
 *
 * Cribbed from SQ/repos/java/browse/compression, but basically delegates to Snappy instance.
 */
class SnappyCompressionCodec : CompressionCodec {
  override fun encode(input: ByteArray): ByteArray {
    return try {
      Snappy.compress(input)
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  override fun decode(input: ByteArray): ByteArray {
    return try {
      Snappy.uncompress(input)
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
  }
}