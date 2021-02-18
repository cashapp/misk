package misk.exceptions

/** Common status codes for actions */
enum class StatusCode(val code: Int) {
  BAD_REQUEST(400),
  NOT_FOUND(404),
  UNAUTHENTICATED(401),
  FORBIDDEN(403),
  NOT_ACCEPTABLE(406),
  CONFLICT(409),
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
open class ActionException(
  val statusCode: StatusCode,
  message: String = statusCode.name,
  cause: Throwable? = null
) : Exception(message, cause)

/** Base exception for when resources are not found */
open class NotFoundException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.NOT_FOUND, message, cause)

/** Base exception for when authentication fails */
open class UnauthenticatedException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.UNAUTHENTICATED, message, cause)

/** Base exception for when authenticated credentials lack access to a resource */
open class UnauthorizedException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.FORBIDDEN, message, cause)

/** Base exception for when a resource is unavailable */
open class ResourceUnavailableException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.SERVICE_UNAVAILABLE, message, cause)

/** Base exception for bad client requests */
open class BadRequestException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.BAD_REQUEST, message, cause)

/** Base exception for when a request causes a conflict */
open class ConflictException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.CONFLICT, message, cause)

open class UnprocessableEntityException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.UNPROCESSABLE_ENTITY, message, cause)

open class TooManyRequestsException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.TOO_MANY_REQUESTS, message, cause)

/** Base exception for when a server is acting as a gateway and cannot get a response in time */
open class GatewayTimeoutException(message: String = "", cause: Throwable? = null) :
    ActionException(StatusCode.GATEWAY_TIMEOUT, message, cause)

/** Similar to [kotlin.require], but throws [BadRequestException] if the check fails */
inline fun requireRequest(check: Boolean, lazyMessage: () -> String) {
  if (!check) throw BadRequestException(lazyMessage())
}
