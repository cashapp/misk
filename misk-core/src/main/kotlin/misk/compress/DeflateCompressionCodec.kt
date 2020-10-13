package com.squareup.misk.compress

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.Deflater.DEFAULT_COMPRESSION
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterOutputStream

/**
 * A {@link CompressionCodec} implementation for the deflate compression format. It uses the
 * implementation provided in the standard {@code java.util.zip} package.
 *
 * Cribbed from SQ/repos/java/browse/compression.
 */
class DeflateCompressionCodec(private val compressionLevel: Int = DEFAULT_COMPRESSION) :
    CompressionCodec {
  init {
    check(-1 <= compressionLevel && compressionLevel <= 9) {
      "Invalid compression level $compressionLevel"
    }
  }

  override fun encode(input: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    val deflater = Deflater(compressionLevel)
    try {
      DeflaterOutputStream(out, deflater).use { compressed -> compressed.write(input) }
    } catch (e: IOException) {
      throw RuntimeException(e)
    } finally {
      deflater.end()
    }
    return out.toByteArray()
  }

  /**
   * Decompresses `input` bytes using a [InflaterOutputStream].
   *
   * @param input bytes to be decoded
   * @return decoded bytes
   */
  override fun decode(input: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    try {
      InflaterOutputStream(out).use { inflate -> inflate.write(input) }
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
    return out.toByteArray()
  }
}