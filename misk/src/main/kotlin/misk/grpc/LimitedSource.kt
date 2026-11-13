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
