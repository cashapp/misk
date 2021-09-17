package misk.web.exceptions

import com.squareup.wire.AnyMessage
import com.squareup.wire.GrpcStatus

data class GrpcErrorResponse(
  val status: GrpcStatus,
  val message: String?,
  val details: List<AnyMessage> = listOf(),
) {
  // backward compatibility
  constructor(status: GrpcStatus, message: String?) : this(status, message, listOf())

  companion object {
    val INTERNAL_SERVER_ERROR = GrpcErrorResponse(GrpcStatus.UNKNOWN, "internal server error")
  }

  // backward compatibility
  fun copy(status: GrpcStatus = this.status, message: String? = this.message) =
    GrpcErrorResponse(status, message)
}
