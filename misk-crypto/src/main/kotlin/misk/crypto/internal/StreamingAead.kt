package misk.crypto.internal

import com.google.crypto.tink.StreamingAead
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel
import java.nio.channels.WritableByteChannel

/**
 * Decorates {@link com.google.crypto.tink.Aead} to collect metrics on operations.
 */
class StreamingAead(
  private val key: misk.crypto.Key,
  private val aead: com.google.crypto.tink.StreamingAead,
  private val metrics: KeyMetrics,
) : StreamingAead by aead {

  override fun newEncryptingChannel(
    ciphertextDestination: WritableByteChannel?,
    associatedData: ByteArray?
  ): WritableByteChannel {
    TODO("Not yet implemented")
  }

  override fun newSeekableDecryptingChannel(
    ciphertextSource: SeekableByteChannel?,
    associatedData: ByteArray?
  ): SeekableByteChannel {
    TODO("Not yet implemented")
  }

  override fun newDecryptingChannel(
    ciphertextSource: ReadableByteChannel?,
    associatedData: ByteArray?
  ): ReadableByteChannel {
    TODO("Not yet implemented")
  }

  override fun newEncryptingStream(
    ciphertextDestination: OutputStream?,
    associatedData: ByteArray?
  ): OutputStream {
    return MeasuredOutputStream(
      key,
      aead.newEncryptingStream(ciphertextDestination, associatedData),
      metrics
    )
  }

  override fun newDecryptingStream(
    ciphertextSource: InputStream?,
    associatedData: ByteArray?
  ): InputStream {
    return MeasuredInputStream(
      key,
      aead.newDecryptingStream(ciphertextSource, associatedData),
      metrics
    )
  }

  //  override fun encrypt(plaintext: ByteArray?, associatedData: ByteArray?): ByteArray {
//    return aead.encrypt(plaintext, associatedData).also {
//      metrics.encrypt(key, plaintext?.size ?: 0)
//    }
//  }
//
//  override fun decrypt(ciphertext: ByteArray?, associatedData: ByteArray?): ByteArray {
//    return aead.decrypt(ciphertext, associatedData).also {
//      metrics.decrypt(key, it.size)
//    }
//  }

}
