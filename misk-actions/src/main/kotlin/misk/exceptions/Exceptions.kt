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

  constructor(
    code: Int,
    message: String,
    cause: Throwable? = null
  ) : this(code, message, message, cause)
}

/** Base exception for when resources are not found */
open class NotFoundException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_NOT_FOUND, message, cause)

/** Base exception for when authentication fails */
open class UnauthenticatedException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNAUTHORIZED, message, cause)

/** Base exception for when authenticated credentials lack access to a resource */
open class UnauthorizedException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_FORBIDDEN, message, cause)

/**
 * Base exception for when a resource is unavailable. The message is not exposed to the caller.
 */
open class ResourceUnavailableException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNAVAILABLE, "RESOURCE_UNAVAILABLE", message, cause)

/** Base exception for bad client requests */
open class BadRequestException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_BAD_REQUEST, message, cause)

/** Base exception for when a request causes a conflict */
open class ConflictException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_CONFLICT, message, cause)

/** This exception is custom to Misk. */
open class UnprocessableEntityException(message: String = "", cause: Throwable? = null) :
  WebActionException(422, message, cause)

/** This exception is custom to Misk. */
open class TooManyRequestsException(message: String = "", cause: Throwable? = null) :
  WebActionException(429, message, cause)

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
  WebActionException(HTTP_ENTITY_TOO_LARGE, message, cause)

open class UnsupportedMediaTypeException(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNSUPPORTED_TYPE, message, cause)

/** Similar to [kotlin.require], but throws [BadRequestException] if the check fails */
inline fun requireRequest(check: Boolean, lazyMessage: () -> String) {
  if (!check) throw BadRequestException(lazyMessage())
}
