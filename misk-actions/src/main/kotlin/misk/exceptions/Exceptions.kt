package misk.exceptions

import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.net.HttpURLConnection.HTTP_UNSUPPORTED_TYPE

/** Common status codes for actions */
@Deprecated("Use java.net.HttpURLConnection.* or status code ints directly instead")
enum class StatusCode(val code: Int) {
  BAD_REQUEST(400),
  NOT_FOUND(404),
  UNAUTHENTICATED(401),
  FORBIDDEN(403),
  NOT_ACCEPTABLE(406),
  CONFLICT(409),
  PAYLOAD_TOO_LARGE(413),
  UNSUPPORTED_MEDIA_TYPE(415),
  ENHANCE_YOUR_CALM(420),
  UNPROCESSABLE_ENTITY(422),
  TOO_MANY_REQUESTS(429),
  CLIENT_CLOSED_REQUEST(499),
  INTERNAL_SERVER_ERROR(500),
  SERVICE_UNAVAILABLE(503),
  GATEWAY_TIMEOUT(504);

  val isClientError = code in (400..499)
  val isServerError = code in (500..599)
}

/** Base class for exceptions thrown by actions, translated into responses */
@Deprecated("Use WebActionException instead")
open class ActionException(
  val statusCode: StatusCode,
  message: String = statusCode.name,
  cause: Throwable? = null
) : Exception(message, cause)

open class WebActionException(
  /** The HTTP status code. Should be 400..599. */
  val code: Int,
  /**
   * This is returned to the caller as is for 4xx responses. 5xx responses are always masked.
   * Be mindful not to leak internal implementation details and possible vulnerabilities in the
   * response body.
   */
  val responseBody: String,
  /**
   * This is logged as the exception message.
   */
  message: String,
  cause: Throwable? = null
) : Exception(message, cause) {
  val isClientError = code in (400..499)
  val isServerError = code in (500..599)

  @Deprecated("Use code instead")
  val statusCode: StatusCode
    get() = StatusCode.values().find { it.code == code }
      ?: throw IllegalStateException("Response code is not in StatusCode. Use `code` instead")
}

/** Base exception for when resources are not found */
open class NotFoundException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_NOT_FOUND, message, message, cause)

/** Base exception for when authentication fails */
open class UnauthenticatedException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNAUTHORIZED, message, message, cause)

/** Base exception for when authenticated credentials lack access to a resource */
open class UnauthorizedException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_FORBIDDEN, message, message, cause)

/**
 * Base exception for when a resource is unavailable. The message is not exposed to the caller.
 */
open class ResourceUnavailableException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNAVAILABLE, "RESOURCE_UNAVAILABLE", message, cause)

/** Base exception for bad client requests */
open class BadRequestException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_BAD_REQUEST, message, message, cause)

/** Base exception for when a request causes a conflict */
open class ConflictException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_CONFLICT, message, message, cause)

/** This exception is custom to Misk. */
open class UnprocessableEntityException(message: String = "", cause: Throwable? = null) :
  WebActionException(422, message, message, cause)

/** This exception is custom to Misk. */
open class TooManyRequestsException(message: String = "", cause: Throwable? = null) :
  WebActionException(429, message, message, cause)

/** This exception is custom to Misk. */
open class ClientClosedRequestException(message: String = "", cause: Throwable? = null) :
  WebActionException(499, message, message, cause)

/**
 * Base exception for when a server is acting as a gateway and cannot get a response in time.
 * The message is not exposed to the caller.
 */
open class GatewayTimeoutException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", message, cause)

open class PayloadTooLargeException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_ENTITY_TOO_LARGE, message, message, cause)

open class UnsupportedMediaTypeException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNSUPPORTED_TYPE, message, message, cause)

/** Similar to [kotlin.require], but throws [BadRequestException] if the check fails */
inline fun requireRequest(check: Boolean, lazyMessage: () -> String) {
  if (!check) throw BadRequestException(lazyMessage())
}
