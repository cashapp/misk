package misk.hibernate

import jakarta.inject.Inject
import javax.persistence.OptimisticLockException
import misk.exceptions.ConflictException
import misk.exceptions.TooManyRequestsException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.exceptions.ExceptionMapper
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.GenericJDBCException
import org.slf4j.event.Level

internal class RetryTransactionExceptionMapper
@Inject
internal constructor(val config: HibernateExceptionLogLevelConfig) : ExceptionMapper<RetryTransactionException> {

  override fun toResponse(th: RetryTransactionException): Response<ResponseBody> =
    ConflictExceptionResponder.toResponse()

  override fun loggingLevel(th: RetryTransactionException) = config.log_level
}

internal class ConstraintViolationExceptionMapper
@Inject
internal constructor(val config: HibernateExceptionLogLevelConfig) : ExceptionMapper<ConstraintViolationException> {
  override fun toResponse(th: ConstraintViolationException): Response<ResponseBody> =
    ConflictExceptionResponder.toResponse()

  override fun loggingLevel(th: ConstraintViolationException) = config.log_level
}

internal class OptimisticLockExceptionMapper
@Inject
internal constructor(val config: HibernateExceptionLogLevelConfig) : ExceptionMapper<OptimisticLockException> {
  override fun toResponse(th: OptimisticLockException): Response<ResponseBody> = ConflictExceptionResponder.toResponse()

  override fun loggingLevel(th: OptimisticLockException) = config.log_level
}

internal class ResourceExhaustedExceptionMapper @Inject internal constructor() : ExceptionMapper<GenericJDBCException> {
  private val HEADERS = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)

  override fun toResponse(th: GenericJDBCException): Response<ResponseBody> =
    Response("TOO_MANY_REQUESTS".toResponseBody(), HEADERS, statusCode = TooManyRequestsException().code)

  override fun loggingLevel(th: GenericJDBCException) = Level.WARN
}

internal object ConflictExceptionResponder {
  private val HEADERS = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
  private val CONFLICT_EXCEPTION = ConflictException()

  fun toResponse(): Response<ResponseBody> {
    val message = "CONFLICT_EXCEPTION"
    val statusCode = CONFLICT_EXCEPTION.code
    return Response(message.toResponseBody(), HEADERS, statusCode = statusCode)
  }
}
