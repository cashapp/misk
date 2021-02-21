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
 * https://github.com/square/wire/search?q=GrpcMessageSource&type=Code
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
          "message is encoded but message-encoding header was omitted"
        )
      }
      else -> throw ProtocolException("unexpected compressed-flag: $compressedFlag")
    }

    val encodedLength = source.readInt().toLong() and 0xffffffffL

    val encodedMessage = Buffer().write(source, encodedLength)

    return messageDecoding.decode(encodedMessage).buffer().use {
      messageAdapter.decode(it)
    }
  }

  override fun toString() = "GrpcMessageSource"
}
