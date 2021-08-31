package misk.web.exceptions

import com.squareup.wire.AnyMessage
import com.squareup.wire.GrpcStatus

data class GrpcErrorResponse(
  val status: GrpcStatus,
  val message: String?,
  val details: List<AnyMessage> = listOf(),
) {
  companion object {
    val INTERNAL_SERVER_ERROR = GrpcErrorResponse(GrpcStatus.UNKNOWN, "internal server error")
  }
}
