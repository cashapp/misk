package misk.grpc

import com.squareup.wire.ProtoAdapter
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import java.io.Closeable
import java.net.ProtocolException

/** Reads an HTTP/2 stream as a sequence of GRPC messages. */
internal class GrpcReader<T> private constructor(
  private val source: BufferedSource,
  private val messageAdapter: ProtoAdapter<T>,
  private val grpcEncoding: GrpcEncoding? = null
) : Closeable {
  companion object {
    /**
     * @param source the HTTP/2 stream body.
     * @param messageAdapter a proto adapter for each message.
     * @param grpcEncoding the "grpc-encoding" header, or null if it is absent.
     */
    fun <T> get(
      source: BufferedSource,
      messageAdapter: ProtoAdapter<T>,
      grpcEncoding: String? = null
    ) = GrpcReader(source, messageAdapter, grpcEncoding?.toGrpcEncoding())
  }

  /**
   * Read the next length-prefixed message on the stream and return it. Returns null if there are
   * no further messages on this stream.
   */
  fun readMessage(): T? {
    if (source.exhausted()) return null

    // Length-Prefixed-Message → Compressed-Flag Message-Length Message
    //         Compressed-Flag → 0 / 1 # encoded as 1 byte unsigned integer
    //          Message-Length → {length of Message} # encoded as 4 byte unsigned integer
    //                 Message → *{binary octet}

    val compressedFlag = source.readByte()
    val messageEncoding: GrpcEncoding = when {
      compressedFlag.toInt() == 0 -> IdentityGrpcEncoding
      compressedFlag.toInt() == 1 -> {
        grpcEncoding ?: throw ProtocolException(
            "message is encoded but message-encoding header was omitted")
      }
      else -> throw ProtocolException("unexpected compressed-flag: $compressedFlag")
    }

    val encodedLength = source.readInt().toLong() and 0xffffffffL

    val encodedMessage = Buffer()
    encodedMessage.write(source, encodedLength)

    return messageAdapter.decode(messageEncoding.decode(encodedMessage).buffer())
  }

  override fun close() {
    source.close()
  }
}
