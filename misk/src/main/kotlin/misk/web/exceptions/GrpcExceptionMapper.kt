package misk.web.exceptions

import com.squareup.wire.GrpcException
import org.slf4j.event.Level
import javax.inject.Inject

internal class GrpcExceptionMapper @Inject internal constructor() : ExceptionMapper<GrpcException> {
  override fun toResponse(th: GrpcException) = IOExceptionMapper.INTERNAL_SERVER_ERROR_RESPONSE

  override fun loggingLevel(th: GrpcException): Level = Level.ERROR
}
