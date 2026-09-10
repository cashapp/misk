package misk.grpc

import java.io.IOException
import okio.Buffer
import okio.ForwardingSource
import okio.Source

/** Thrown when an inbound gRPC message exceeds the configured maximum size. */
class GrpcMessageTooLargeException(maxMessageBytes: Long) :
  IOException("gRPC message exceeds the maximum allowed size of $maxMessageBytes bytes")

/**
 * A [Source] that aborts with [GrpcMessageTooLargeException] once more than [maxBytes] bytes have been read from
 * [delegate]. Because it counts bytes as they are produced, it bounds the memory a decompressing source (such as gzip)
 * can allocate, preventing a small compressed frame from inflating into an out-of-memory-sized message.
 */
internal class LimitedSource(delegate: Source, private val maxBytes: Long) : ForwardingSource(delegate) {
  private var bytesRead = 0L

  override fun read(sink: Buffer, byteCount: Long): Long {
    val read = super.read(sink, byteCount)
    if (read == -1L) return -1L
    bytesRead += read
    if (bytesRead > maxBytes) {
      throw GrpcMessageTooLargeException(maxBytes)
    }
    return read
  }
}

/** Messages decoding to at most this many bytes are always allowed, regardless of compression ratio. */
internal const val GRPC_MIN_DECODED_MESSAGE_BYTES = 4L * 1024 * 1024

/**
 * The largest decoded message allowed for a frame of [compressedBytes] on the wire under [maxDecompressionRatio]. The
 * decoded size may exceed [GRPC_MIN_DECODED_MESSAGE_BYTES] only by [maxDecompressionRatio] times the on-the-wire size,
 * so a small compressed frame cannot inflate into a heap-exhausting message while genuinely large (low-ratio) payloads
 * still pass. A [maxDecompressionRatio] below 1 is treated as 1, and an overflowing product is treated as unbounded.
 */
internal fun maxDecodedMessageBytes(compressedBytes: Long, maxDecompressionRatio: Long): Long {
  val ratio = maxDecompressionRatio.coerceAtLeast(1L)
  val scaled =
    try {
      Math.multiplyExact(compressedBytes, ratio)
    } catch (e: ArithmeticException) {
      Long.MAX_VALUE
    }
  return maxOf(GRPC_MIN_DECODED_MESSAGE_BYTES, scaled)
}
