package misk.grpc

import com.squareup.wire.MessageSink
import com.squareup.wire.ProtoAdapter
import okio.Buffer
import okio.BufferedSink

/**
 * Writes a sequence of gRPC messages as an HTTP/2 stream.
 *
 * This is derived from Wire's GrpcMessageSink.kt. https://github.com/square/wire/search?q=GrpcMessageSink&type=Code
 *
 * @param sink the HTTP/2 stream body.
 * @param minMessageToCompress the minimum message size for compression when [grpcEncoding] is not "identity".
 * @param messageAdapter a proto adapter for each message.
 * @param grpcEncoding the content coding for the stream body.
 */
internal class GrpcMessageSink<T : Any>(
  private val sink: BufferedSink,
  private val minMessageToCompress: Long,
  private val messageAdapter: ProtoAdapter<T>,
  private val grpcEncoding: String,
) : MessageSink<T> {
  private var closed = false

  override fun write(message: T) {
    check(!closed) { "closed" }

    val encodedMessage = Buffer()
    messageAdapter.encode(encodedMessage, message)

    if (grpcEncoding == "identity" || encodedMessage.size < minMessageToCompress) {
      sink.writeByte(0) // 0 = Not encoded.
      sink.writeInt(encodedMessage.size.toInt())
      sink.writeAll(encodedMessage)
    } else {
      val compressedMessage = Buffer()
      grpcEncoding.toGrpcEncoder().encode(compressedMessage).use { sink -> sink.writeAll(encodedMessage) }
      sink.writeByte(1) // 1 = Compressed.
      sink.writeInt(compressedMessage.size.toInt())
      sink.writeAll(compressedMessage)
    }

    // TODO: fail if the message size is more than MAX_INT
    sink.flush()
  }

  override fun cancel() {
    check(!closed) { "closed" }
    // TODO: Cancel the Jetty request.
  }

  override fun close() {
    if (closed) return
    closed = true
    sink.close()
  }

  override fun toString() = "GrpcMessageSink"
}
