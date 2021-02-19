package misk.hibernate

import com.google.common.base.Throwables
import misk.exceptions.ConflictException
import misk.exceptions.StatusCode
import misk.web.Response
import misk.web.ResponseBody
import misk.web.exceptions.ExceptionMapper
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.GenericJDBCException
import org.slf4j.event.Level
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import javax.inject.Inject
import javax.persistence.OptimisticLockException

internal class RetryTransactionExceptionMapper @Inject internal constructor() : ExceptionMapper<RetryTransactionException> {

  override fun toResponse(th: RetryTransactionException): Response<ResponseBody> =
    ConflictExceptionResponder.toResponse()

  override fun canHandle(th: Throwable): Boolean {
    val rootCause = Throwables.getRootCause(th)

    return rootCause is SQLIntegrityConstraintViolationException ||
      rootCause is StaleObjectStateException
  }

  override fun loggingLevel(th: RetryTransactionException) = Level.WARN
}

internal class ConstraintViolationExceptionMapper @Inject internal constructor() : ExceptionMapper<ConstraintViolationException> {
  override fun toResponse(th: ConstraintViolationException): Response<ResponseBody> =
    ConflictExceptionResponder.toResponse()

  override fun canHandle(th: Throwable): Boolean = th is ConstraintViolationException

  override fun loggingLevel(th: ConstraintViolationException) = Level.WARN
}

internal class OptimisticLockExceptionMapper @Inject internal constructor() : ExceptionMapper<OptimisticLockException> {
  override fun toResponse(th: OptimisticLockException): Response<ResponseBody> =
    ConflictExceptionResponder.toResponse()

  override fun canHandle(th: Throwable): Boolean = th is OptimisticLockException

  override fun loggingLevel(th: OptimisticLockException) = Level.WARN
}

internal class ResourceExhaustedExceptionMapper @Inject internal constructor() : ExceptionMapper<GenericJDBCException> {
  private val HEADERS: Headers =
    headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)

  override fun toResponse(th: GenericJDBCException): Response<ResponseBody> =
    Response(
      StatusCode.TOO_MANY_REQUESTS.name.toResponseBody(), HEADERS,
      statusCode = StatusCode.TOO_MANY_REQUESTS.code
    )

  override fun canHandle(th: Throwable): Boolean =
    th is GenericJDBCException &&
      th.cause is SQLException &&
      // This GRPC error code is sent from Vitess when we can't get a connection from a pool or
      // if we trigger concurrency limits. This means we are over capacity and need to reject
      // requests until we get within capacity again.
      // Unfortunately the GRPC error code isn't being returned in a structured way so we have
      // to look for this string in the error message.
      th.cause?.message?.contains("Code: RESOURCE_EXHAUSTED") == true

  override fun loggingLevel(th: GenericJDBCException) = Level.WARN
}

internal object ConflictExceptionResponder {
  private val HEADERS: Headers =
    headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
  private val CONFLICT_EXCEPTION = ConflictException()

  fun toResponse(): Response<ResponseBody> {
    val message = CONFLICT_EXCEPTION.statusCode.name
    val statusCode = CONFLICT_EXCEPTION.statusCode.code
    return Response(message.toResponseBody(), HEADERS, statusCode = statusCode)
  }
}
