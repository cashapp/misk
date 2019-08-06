package misk.grpc

import com.squareup.wire.MessageSource
import com.squareup.wire.ProtoAdapter
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import java.io.Closeable
import java.net.ProtocolException

/**
 * Reads an HTTP/2 stream as a sequence of gRPC messages.
 *
 * This is derived from Wire's GrpcMessageSource.kt.
 * https://github.com/square/wire/blob/master/wire-grpc-client/src/main/java/com/squareup/wire/GrpcMessageSource.kt
 *
 * @param source the HTTP/2 stream body.
 * @param messageAdapter a proto adapter for each message.
 * @param grpcEncoding the "grpc-encoding" header, or null if it is absent.
 */
internal class GrpcMessageSource<T : Any>(
  private val source: BufferedSource,
  private val messageAdapter: ProtoAdapter<T>,
  private val grpcEncoding: String? = null
) : MessageSource<T>, Closeable by source {
  override fun read(): T? {
    if (source.exhausted()) return null

    // Length-Prefixed-Message → Compressed-Flag Message-Length Message
    //         Compressed-Flag → 0 / 1 # encoded as 1 byte unsigned integer
    //          Message-Length → {length of Message} # encoded as 4 byte unsigned integer
    //                 Message → *{binary octet}

    val compressedFlag = source.readByte()
    val messageDecoding: GrpcDecoder = when {
      compressedFlag.toInt() == 0 -> GrpcDecoder.IdentityGrpcDecoder
      compressedFlag.toInt() == 1 -> {
        grpcEncoding?.toGrpcDecoding() ?: throw ProtocolException(
            "message is encoded but message-encoding header was omitted")
      }
      else -> throw ProtocolException("unexpected compressed-flag: $compressedFlag")
    }

    val encodedLength = source.readInt().toLong() and 0xffffffffL

    val encodedMessage = Buffer()
    encodedMessage.write(source, encodedLength)

    return messageAdapter.decode(messageDecoding.decode(encodedMessage).buffer())
  }

  override fun toString() = "GrpcMessageSource"
}
