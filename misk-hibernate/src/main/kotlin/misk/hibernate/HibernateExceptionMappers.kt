package misk.hibernate

import com.google.common.base.Throwables
import misk.exceptions.ConflictException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.exceptions.ExceptionMapper
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.event.Level
import java.sql.SQLIntegrityConstraintViolationException
import javax.inject.Inject
import javax.persistence.OptimisticLockException

internal class RetryTransactionExceptionMapper @Inject internal constructor(
) : ExceptionMapper<RetryTransactionException> {

  override fun toResponse(th: RetryTransactionException): Response<ResponseBody>
    = ConflictExceptionResponder.toResponse()

  override fun canHandle(th: Throwable): Boolean {
    val rootCause = Throwables.getRootCause(th)

    return rootCause is SQLIntegrityConstraintViolationException ||
      rootCause is StaleObjectStateException
  }

  override fun loggingLevel(th: RetryTransactionException) = Level.WARN
}

internal class ConstraintViolationExceptionMapper @Inject internal constructor(
) : ExceptionMapper<ConstraintViolationException> {
  override fun toResponse(th: ConstraintViolationException): Response<ResponseBody>
    = ConflictExceptionResponder.toResponse()

  override fun canHandle(th: Throwable): Boolean = th is ConstraintViolationException

  override fun loggingLevel(th: ConstraintViolationException) = Level.WARN
}

internal class OptimisticLockExceptionMapper @Inject internal constructor(
) : ExceptionMapper<OptimisticLockException> {
  override fun toResponse(th: OptimisticLockException): Response<ResponseBody>
    = ConflictExceptionResponder.toResponse()

  override fun canHandle(th: Throwable): Boolean = th is OptimisticLockException

  override fun loggingLevel(th: OptimisticLockException) = Level.WARN
}

internal object ConflictExceptionResponder {
  private val HEADERS: Headers =
    Headers.of(listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap())
  private val CONFLICT_EXCEPTION = ConflictException()

  fun toResponse(): Response<ResponseBody> {
    val message = CONFLICT_EXCEPTION.statusCode.name
    val statusCode = CONFLICT_EXCEPTION.statusCode.code
    return Response(message.toResponseBody(), HEADERS, statusCode = statusCode)
  }
}