package misk.exceptions

import com.squareup.wire.AnyMessage
import com.squareup.wire.GrpcStatus
import java.net.HttpURLConnection.HTTP_BAD_GATEWAY
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.net.HttpURLConnection.HTTP_UNSUPPORTED_TYPE
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Even though all kotlin exceptions are runtime exceptions.
 * To ensure java inter-op all exception need to extend from RuntimeException.
 */
@Suppress("AnnotatePublicApisWithJvmOverloads")
open class WebActionException(
  /** The HTTP status code. Should be 400..599. */
  val code: Int,
  /**
   * This is returned to the caller as is. Be mindful not to leak internal implementation details
   * and possible vulnerabilities in the response body.
   */
  val responseBody: String,
  /**
   * This is logged as the exception message.
   */
  message: String,
  cause: Throwable? = null,
  /**
   * The gPRC status code. If unset, it will be inferred from [code].
   *
   * Reference: https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
   * */
  val grpcStatus: GrpcStatus? = null,
  /**
   * Details are used to enrich gRPC errors with additional proto-encoded messages.
   * error_details.proto is a well-known collection of details, but clients can define their own.
   * This field is ignored for REST responses.
   *
   * Reference: https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto
   */
  val details: List<AnyMessage> = listOf(),
) : RuntimeException(message, cause) {
  val isClientError = grpcStatus?.isGrpcClientCode ?: code in (400..499)
  val isServerError = grpcStatus?.isGrpcServerCode ?: code in (500..599)

  constructor(
    code: Int,
    message: String,
    cause: Throwable? = null
  ) : this(code, message, message, cause)

  constructor( // backward compatibility
    code: Int,
    responseBody: String,
    message: String,
    cause: Throwable? = null,
  ): this(code, responseBody, message, cause, null, listOf())
}

/** Base exception for when resources are not found */
open class NotFoundException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_NOT_FOUND, message, cause)

/** Base exception for when authentication fails */
open class UnauthenticatedException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNAUTHORIZED, message, cause)

/** Base exception for when authenticated credentials lack access to a resource */
open class UnauthorizedException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_FORBIDDEN, message, cause)

/**
 * Base exception for when a resource is unavailable. The message is not exposed to the caller.
 */
open class ResourceUnavailableException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNAVAILABLE, "RESOURCE_UNAVAILABLE", message, cause)

/** Base exception for bad client requests */
open class BadRequestException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_BAD_REQUEST, message, cause)

/** Base exception for when a request causes a conflict */
open class ConflictException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_CONFLICT, message, cause)

/** This exception is custom to Misk. */
open class UnprocessableEntityException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(422, message, cause)

/** This exception is custom to Misk. */
open class TooManyRequestsException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(429, message, cause)

/** This exception is custom to Misk. */
open class ClientClosedRequestException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(499, message, message, cause)

/**
 * Base exception for when a server is acting as a gateway and gets invalid response from upstream.
 * The message is not exposed to the caller.
 */
open class BadGatewayException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_BAD_GATEWAY, "BAD_GATEWAY", message, cause)

/**
 * Base exception for when a server is acting as a gateway and cannot get a response in time.
 * The message is not exposed to the caller.
 */
open class GatewayTimeoutException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", message, cause)

open class PayloadTooLargeException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_ENTITY_TOO_LARGE, message, cause)

open class UnsupportedMediaTypeException @JvmOverloads constructor(message: String = "", cause: Throwable? = null) :
  WebActionException(HTTP_UNSUPPORTED_TYPE, message, cause)

/** Similar to [kotlin.require], but throws [BadRequestException] if the check fails */
@OptIn(ExperimentalContracts::class)
inline fun requireRequest(check: Boolean, lazyMessage: () -> String) {
  contract { 
    returns() implies check
  }
  if (!check) throw BadRequestException(lazyMessage())
}

/** Similar to [kotlin.requireNotNull], but throws [BadRequestException] if the check fails */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireRequestNotNull(value: T?, lazyMessage: () -> String): T {
  contract {
    returns() implies (value != null)
  }

  if (value == null) {
    throw BadRequestException(lazyMessage())
  } else {
    return value
  }
}

private val GrpcStatus.isGrpcServerCode
  get() = this !in setOf(
    GrpcStatus.INVALID_ARGUMENT,
    GrpcStatus.NOT_FOUND,
    GrpcStatus.ALREADY_EXISTS,
    GrpcStatus.FAILED_PRECONDITION,
    GrpcStatus.ABORTED,
    GrpcStatus.OUT_OF_RANGE,
    GrpcStatus.DATA_LOSS,
    GrpcStatus.PERMISSION_DENIED, // the spec doesn't mention if is client only, but sounds like it
  )

private val GrpcStatus.isGrpcClientCode get() = true
