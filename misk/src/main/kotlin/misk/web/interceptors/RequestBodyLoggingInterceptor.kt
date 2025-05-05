package misk.web.interceptors

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.web.interceptors.hooks.RequestResponseHook
import misk.web.interceptors.hooks.RequestResponseLoggingHook
import okhttp3.Headers
import wisp.logging.getLogger

private val logger = getLogger<RequestBodyLoggingInterceptor>()

/**
 * Stores request and response information for an action in a ThreadLocal, to be logged
 * in [RequestLoggingInterceptor]
 *
 * Timing information doesn't count time writing the response to the remote client.
 */
class RequestBodyLoggingInterceptor @Inject internal constructor(
  private val action: Action,
  private val caller: ActionScoped<MiskCaller?>,
  private val bodyCapture: RequestResponseCapture,
) : ApplicationInterceptor {

  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val bodyCapture: RequestResponseCapture,
    private val requestResponseHookFactories: List<RequestResponseHook.Factory>,
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
      // Only bother with endpoints that have a hook annotation
      val requestResponseHooks = requestResponseHookFactories.mapNotNull { it.create(action) }
      if (requestResponseHooks.isEmpty()) return null

      requestResponseHooks.forEach { hook ->
        when (hook) {
          is RequestResponseLoggingHook -> {
            val config = hook.config

            require(config.bodySampling in 0.0..1.0) {
              "${action.name} @LogRequestResponse bodySampling must be in the range (0.0, 1.0]"
            }
            require(config.errorBodySampling in 0.0..1.0) {
              "${action.name} @LogRequestResponse errorBodySampling must be in the range (0.0, 1.0]"
            }
            if (config.bodySampling == 0.0 && config.errorBodySampling == 0.0) {
              return null
            }
          }
        }
      }

      return RequestBodyLoggingInterceptor(
        action = action,
        caller = caller,
        bodyCapture = bodyCapture
      )
    }
  }

  override fun intercept(chain: Chain): Any {
    val principal = caller.get()?.principal ?: "unknown"

    // Only log some request headers.
    val redactedRequestHeaders = HeadersCapture(chain.httpCall.requestHeaders)
    // Since we already log headers separately, no need to log them if they are in args.
    val args = chain.args.filter { it !is Headers }
    bodyCapture.set(
      RequestResponseBody(
        request = args,
        response = null,
        requestHeaders = redactedRequestHeaders.headers,
        responseHeaders = null,
      )
    )

    try {
      val result = chain.proceed(chain.args)
      val redactedResponseHeaders = HeadersCapture(chain.httpCall.responseHeaders)
      bodyCapture.set(
        RequestResponseBody(
          request = args,
          response = result,
          requestHeaders = redactedRequestHeaders.headers,
          responseHeaders = redactedResponseHeaders.headers,
        )
      )
      return result
    } catch (t: Throwable) {
      logger.info { "${action.name} principal=$principal failed" }
      throw t
    }
  }
}

internal data class HeadersCapture(
  val headers: Map<String, List<String>>
) {
  constructor(okHttpHeaders: Headers) : this(
    okHttpHeaders.toMultimap()
      .filter { (key, _) ->
        key.lowercase() in listOf(
          "accept",
          "accept-encoding",
          "connection",
          "content-type",
          "content-length",
          // Also show tracing headers. These are also in logs, but showing them in the headers
          // gives us more confidence that traces were sent from service to service.
          "x-b3-traceid",
          "x-b3-spanid",
          "x-ddtrace-parent_trace_id",
          "x-ddtrace-parent_span_id",
          "x-datadog-parent-id",
          "x-datadog-trace-id",
          "x-datadog-sampling-priority",
          "x-request-id",
        )
      }
  )
}

internal class RequestResponseCapture @Inject constructor() {
  companion object {
    private val capture = ThreadLocal<RequestResponseBody>()
  }

  fun get(): RequestResponseBody? = capture.get()

  fun set(value: RequestResponseBody) {
    capture.set(value)
  }

  fun clear() {
    return capture.remove()
  }
}

data class RequestResponseBody @JvmOverloads constructor(
  val request: Any?,
  val response: Any?,
  val requestHeaders: Any? = null,
  val responseHeaders: Any? = null,
)

/**
 * Transforms request and/or response bodies before they get logged by [RequestLoggingInterceptor].
 * Useful for things like stripping out noisy data.
 *
 * Note that the order in which `RequestLoggingTransformer`s get applied is considered undefined
 * and cannot be reliably controlled.
 */
interface RequestLoggingTransformer {
  fun transform(requestResponseBody: RequestResponseBody?): RequestResponseBody?
}

fun RequestLoggingTransformer.tryTransform(requestResponseBody: RequestResponseBody?): RequestResponseBody? =
  try {
    transform(requestResponseBody)
  } catch (ex: Exception) {
    logger.warn(ex) {
      "RequestLoggingTransformer of type [${this.javaClass.name}] failed to transform: request=${requestResponseBody?.request} response=${requestResponseBody?.response}"
    }
    requestResponseBody
  }
