package misk.grpc

import okio.BufferedSource
import okio.GzipSource
import okio.Source
import java.net.ProtocolException

/**
 * This is derived from Wire's GrpcDecoder.kt.
 * https://github.com/square/wire/blob/master/wire-grpc-client/src/main/java/com/squareup/wire/GrpcDecoder.kt
 */
internal sealed class GrpcDecoder(val name: String) {
  /** Returns a stream that decodes `source`. */
  abstract fun decode(source: BufferedSource): Source

  internal object IdentityGrpcDecoder : GrpcDecoder("identity") {
    override fun decode(source: BufferedSource) = source
  }

  internal object GzipGrpcDecoder : GrpcDecoder("gzip") {
    override fun decode(source: BufferedSource) = GzipSource(source)
  }
}

internal fun String.toGrpcDecoding(): GrpcDecoder {
  return when (this) {
    "identity" -> GrpcDecoder.IdentityGrpcDecoder
    "gzip" -> GrpcDecoder.GzipGrpcDecoder
    "deflate" -> throw ProtocolException("deflate not yet supported")
    "snappy" -> throw ProtocolException("snappy not yet supported")
    else -> throw ProtocolException("unsupported grpc-encoding: $this")
  }
}
