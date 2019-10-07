package misk.grpc

import com.squareup.wire.MessageSink
import com.squareup.wire.ProtoAdapter
import okio.Buffer
import okio.BufferedSink

/**
 * Writes a sequence of gRPC messages as an HTTP/2 stream.
 *
 * This is derived from Wire's GrpcMessageSink.kt.
 * https://github.com/square/wire/search?q=GrpcMessageSink&type=Code
 *
 * @param sink the HTTP/2 stream body.
 * @param messageAdapter a proto adapter for each message.
 * @param grpcEncoding the content coding for the stream body.
 */
internal class GrpcMessageSink<T : Any> constructor(
  private val sink: BufferedSink,
  private val messageAdapter: ProtoAdapter<T>,
  private val grpcEncoding: String
) : MessageSink<T> {
  private var closed = false
  override fun write(message: T) {
    check(!closed) { "closed" }

    val encodedMessage = Buffer()
    grpcEncoding.toGrpcEncoder().encode(encodedMessage).use { encodingSink ->
      messageAdapter.encode(encodingSink, message)
    }

    val compressedFlag = if (grpcEncoding == "identity") 0 else 1
    sink.writeByte(compressedFlag)
    // TODO: fail if the message size is more than MAX_INT
    sink.writeInt(encodedMessage.size.toInt())
    sink.writeAll(encodedMessage)
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
