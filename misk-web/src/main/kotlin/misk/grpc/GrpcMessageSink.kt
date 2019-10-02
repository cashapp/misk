package misk.grpc

import com.squareup.wire.MessageSink
import com.squareup.wire.ProtoAdapter
import okio.Buffer
import okio.BufferedSink
import java.io.Closeable

/**
 * Writes a sequence of gRPC messages as an HTTP/2 stream.
 *
 * This is derived from Wire's GrpcMessageSink.kt.
 * https://github.com/square/wire/blob/master/wire-grpc-client/src/main/java/com/squareup/wire/GrpcMessageSink.kt
 *
 * @param sink the HTTP/2 stream body.
 * @param messageAdapter a proto adapter for each message.
 * @param grpcEncoding the content coding for the stream body.
 */
internal class GrpcMessageSink<T : Any> constructor(
  private val sink: BufferedSink,
  private val messageAdapter: ProtoAdapter<T>,
  private val grpcEncoding: String = "identity"
) : MessageSink<T>, Closeable by sink {
  override fun write(message: T) {
    val messageEncoding = grpcEncoding.toGrpcEncoder()
    val encodingSink = messageEncoding.encode(sink)

    val compressedFlag = if (grpcEncoding == "identity") 0 else 1
    encodingSink.writeByte(compressedFlag)

    val encodedMessage = Buffer()
    messageAdapter.encode(encodedMessage, message)

    // TODO: fail if the message size is more than MAX_INT
    encodingSink.writeInt(encodedMessage.size.toInt())
    encodingSink.writeAll(encodedMessage)

    sink.flush()
  }

  override fun toString() = "GrpcMessageSink"
}