package misk.grpc

import okio.BufferedSink
import okio.buffer
import okio.gzip
import java.net.ProtocolException

/**
 * This is derived from Wire's GrpcEncoder.kt.
 * https://github.com/square/wire/blob/master/wire-grpc-client/src/main/java/com/squareup/wire/GrpcEncoder.kt
 */
internal sealed class GrpcEncoder(val name: String) {
  /** Returns a stream that decodes `source`. */
  abstract fun encode(sink: BufferedSink): BufferedSink

  internal object IdentityGrpcEncoder : GrpcEncoder("identity") {
    override fun encode(sink: BufferedSink) = sink
  }

  internal object GzipGrpcEncoder : GrpcEncoder("gzip") {
    override fun encode(sink: BufferedSink) = sink.gzip().buffer()
  }
}

internal fun String.toGrpcEncoder(): GrpcEncoder {
  return when (this) {
    "identity" -> GrpcEncoder.IdentityGrpcEncoder
    "gzip" -> GrpcEncoder.GzipGrpcEncoder
    "deflate" -> throw ProtocolException("deflate not yet supported")
    "snappy" -> throw ProtocolException("snappy not yet supported")
    else -> throw ProtocolException("unsupported grpc-encoding: $this")
  }
}
