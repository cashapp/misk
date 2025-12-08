package misk.web.exceptions

import com.google.common.util.concurrent.UncheckedExecutionException
import com.squareup.wire.GrpcStatus
import com.squareup.wire.ProtoAdapter
import jakarta.inject.Inject
import misk.Action
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.grpc.GrpcMessageSink
import misk.proto.Status
import misk.web.DispatchMechanism
import misk.web.HttpCall
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.ResponseBody
import misk.web.interceptors.hooks.RequestResponseLoggedCapture
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import misk.logging.SmartTagsThreadLocalHandler
import misk.logging.Tag
import misk.logging.error
import misk.logging.getLogger
import misk.logging.log
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.Base64

/**
 * Converts and logs application and component level dispatch exceptions into the appropriate
 * response format. Allows application and component code to control how exceptions are
 * represented to clients; for example by setting the status code appropriately, or by returning
 * a specialized response format specific to the error. Components can control how exceptions are
 * mapped by installing [ExceptionMapper] via the [ExceptionMapperModule]
 *
 * TODO(isabel): Set the response body in a ThreadLocal to log in [RequestLoggingInterceptor]
 */
class ExceptionHandlingInterceptor private constructor(
  private val actionName: String,
  private val mapperResolver: ExceptionMapperResolver,
  private val requestResponseLoggedCapture: RequestResponseLoggedCapture,
) : NetworkInterceptor {

  @OptIn(ExperimentalMiskApi::class)
  override fun intercept(chain: NetworkChain) {
    try {
      chain.proceed(chain.httpCall)
    } catch (th: Throwable) {
      try {
        val mdcTags = SmartTagsThreadLocalHandler.popThreadLocalSmartTags()

        if (chain.httpCall.dispatchMechanism == DispatchMechanism.GRPC) {
          // This response object is only used for determining the status code. toGrpcResponse
          // will provide a more useful log instead.
          val response = toResponse(th, suppressLog = true, mdcTags)
          sendGrpcFailure(chain.httpCall, response.statusCode, toGrpcResponse(th, mdcTags))
        } else {
          val response = toResponse(th, suppressLog = requestResponseLoggedCapture.isLogged(), mdcTags)
          chain.httpCall.statusCode = response.statusCode
          sendHttpFailure(chain.httpCall, response)
        }
      } catch (e: IOException) {
        // We failed to write the response for some reason.
        log.info(e) { "failed to write the response" }

      }
    }
  }

  private fun sendHttpFailure(httpCall: HttpCall, response: Response<*>) {
    httpCall.takeResponseBody()?.use { sink ->
      httpCall.addResponseHeaders(response.headers)
      (response.body as ResponseBody).writeTo(sink)
    }
  }

  /**
   * Borrow behavior from [GrpcFeatureBinding] to send a gRPC error with an HTTP 200 status code.
   * This is weird but it's how gRPC clients work.
   *
   * One thing to note is for our metrics we want to pretend that the HTTP code is what we sent.
   * Otherwise gRPC requests that crashed and yielded an HTTP 200 code will confuse operators.
   */
  private fun sendGrpcFailure(httpCall: HttpCall, httpStatus: Int, response: GrpcErrorResponse) {
    // set the statusCode to what we would've sent for an HTTP error so that metrics on HTTP
    // status continue to count errors, instead of forcing them all to "200" on account of gRPC.
    httpCall.setStatusCodes(httpStatus, 200)
    httpCall.requireTrailers()
    httpCall.setResponseHeader("grpc-encoding", "identity")
    httpCall.setResponseHeader("Content-Type", MediaTypes.APPLICATION_GRPC)
    httpCall.setResponseTrailer(
      "grpc-status",
      response.status.code.toString()
    )
    httpCall.setResponseTrailer("grpc-status-details-bin", response.toEncodedStatusProto)
    val message = response.message ?: response.status.name
    val encoded = message.replace("\n", "%0A")
    httpCall.setResponseTrailer("grpc-message", encoded)
    httpCall.takeResponseBody()?.use { responseBody: BufferedSink ->
      GrpcMessageSink(
        sink = responseBody,
        minMessageToCompress = 0,
        messageAdapter = ProtoAdapter.BYTES,
        grpcEncoding = "identity"
      ).use { messageSink ->
        messageSink.write(ByteString.EMPTY)
      }
    }
  }

  private fun grpcMessage(response: Response<*>): String {
    val buffer = Buffer()
    (response.body as ResponseBody).writeTo(buffer)
    return buffer.readUtf8()
  }

  private fun toResponse(th: Throwable, suppressLog: Boolean, mdcTags: Set<Tag>): Response<*> =
    unwrappedToResponse(th.unwrap(), suppressLog, mdcTags)

  private fun unwrappedToResponse(th: Throwable, suppressLog: Boolean, mdcTags: Set<Tag>): Response<*> =
    // Prefer the mapper's response, if one exists.
    mapperResolver.mapperFor(th)?.let {
      if (!suppressLog && it.isError(th)) {
        log.log(
          level = it.loggingLevel(th),
          th = th,
          tags = mdcTags.toTypedArray(),
        ) { "exception dispatching to $actionName" }
      }

      it.toResponse(th)
    }
    // Fall back to a default mapping.
      ?: toInternalServerError(th, suppressLog, mdcTags)


  private fun toGrpcResponse(th: Throwable, mdcTags: Set<Tag>): GrpcErrorResponse =
    unwrappedToGrpcResponse(th.unwrap(), mdcTags)

  private fun unwrappedToGrpcResponse(th: Throwable, mdcTags: Set<Tag>): GrpcErrorResponse = when (th) {
    is UnauthenticatedException -> GrpcErrorResponse(GrpcStatus.UNAUTHENTICATED, th.message)
    is UnauthorizedException -> GrpcErrorResponse(GrpcStatus.PERMISSION_DENIED, th.message)
    else -> mapperResolver.mapperFor(th)?.let {
      if (!requestResponseLoggedCapture.isLogged()) {
        log.log(
          level = it.loggingLevel(th),
          th = th,
          tags = mdcTags.toTypedArray(),
        ) { "exception dispatching to $actionName" }
      }
      val grpcResponse = it.toGrpcResponse(th)
      if (grpcResponse == null) {
        val httpResponse = toResponse(th, suppressLog = true, mdcTags)
        GrpcErrorResponse(toGrpcStatus(httpResponse.statusCode), grpcMessage(httpResponse))
      } else {
        grpcResponse
      }
    } ?: GrpcErrorResponse.internalServerError(th)
  }

  private fun toInternalServerError(th: Throwable, suppressLog: Boolean, mdcTags: Set<Tag>): Response<*> {
    if (!suppressLog) {
      log.error(th = th, tags = mdcTags.toTypedArray()) { "unexpected error dispatching to $actionName" }
    }
    return INTERNAL_SERVER_ERROR_RESPONSE
  }

  class Factory @Inject internal constructor(
    private val mapperResolver: ExceptionMapperResolver,
    private val requestResponseLoggedCapture: RequestResponseLoggedCapture,
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action) =
      ExceptionHandlingInterceptor(action.name, mapperResolver, requestResponseLoggedCapture)
  }

  private companion object {
    val log = getLogger<ExceptionHandlingInterceptor>()

    val INTERNAL_SERVER_ERROR_RESPONSE = Response(
      "internal server error".toResponseBody(),
      headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
      HttpURLConnection.HTTP_INTERNAL_ERROR
    )
  }
}

/** https://grpc.github.io/grpc/core/md_doc_http-grpc-status-mapping.html */
fun toGrpcStatus(statusCode: Int): GrpcStatus {
  return when (statusCode) {
    400 -> GrpcStatus.INVALID_ARGUMENT
    401 -> GrpcStatus.UNAUTHENTICATED
    403 -> GrpcStatus.PERMISSION_DENIED
    404 -> GrpcStatus.NOT_FOUND
    409 -> GrpcStatus.ALREADY_EXISTS
    429 -> GrpcStatus.UNAVAILABLE
    502 -> GrpcStatus.UNAVAILABLE
    503 -> GrpcStatus.UNAVAILABLE
    504 -> GrpcStatus.UNAVAILABLE
    else -> GrpcStatus.UNKNOWN
  }
}

/**
 * Unwrap [InvocationTargetException] and [UncheckedExecutionException] to find the root cause.
 */
internal fun Throwable.unwrap(): Throwable {
  var th = this
  while (true) {
    th = when (th) {
      is InvocationTargetException -> th.targetException
      is UncheckedExecutionException -> th.cause!!
      else -> return th
    }
  }
}

/** Convert to a compatible base64-proto-encoded google.rpc.Status-compatible value. */
private val GrpcErrorResponse.toEncodedStatusProto
  get() : String {
    val status = Status(
      code = status.code, // must match "grpc-status"
      message = message ?: status.name, // must match "grpc-message"
      details = details
    )
    // In gRPC, base64-encoded binary fields must be un-padded.
    return Base64.getEncoder().withoutPadding().encodeToString(Status.ADAPTER.encode(status))
  }
