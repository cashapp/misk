package misk.grpc

import okio.BufferedSource
import okio.GzipSource
import okio.Source
import java.net.ProtocolException

internal abstract class GrpcEncoding(val name: String) {
  /** Returns a stream that decodes `source`. */
  abstract fun decode(source: BufferedSource): Source
}

internal object IdentityGrpcEncoding : GrpcEncoding("identity") {
  override fun decode(source: BufferedSource) = source
}

internal object GzipGrpcEncoding : GrpcEncoding("gzip") {
  override fun decode(source: BufferedSource) = GzipSource(source)
}

internal fun String.toGrpcEncoding(): GrpcEncoding {
  return when (this) {
    "identity" -> IdentityGrpcEncoding
    "gzip" -> GzipGrpcEncoding
    "deflate" -> throw ProtocolException("deflate not yet supported") // TODO.
    "snappy" -> throw ProtocolException("snappy not yet supported") // TODO.
    else -> throw ProtocolException("unsupported grpc-encoding: $this")
  }
}
