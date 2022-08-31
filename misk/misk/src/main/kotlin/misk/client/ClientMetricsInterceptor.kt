package misk.client

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.squareup.wire.GrpcMethod
import com.squareup.wire.GrpcStatus
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.metrics.v2.Metrics
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ClientMetricsInterceptor private constructor(
  val clientName: String,
  private val requestDurationSummary: Summary,
  private val requestDurationHistogram: Histogram,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val actionName = actionName(chain)
      ?: return chain.proceed(chain.request())

    val stopwatch = Stopwatch.createStarted(Ticker.systemTicker())
    try {
      val result = chain.proceed(chain.request())
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      // We should read both the headers and trailers for grpc-status but the trailers aren't
      // available yet, so just do the headers
      val grpcStatus = result.headers["grpc-status"]?.toIntOrNull()
      val code = grpcStatusToHttpCode(grpcStatus) ?: result.code
      requestDurationSummary.labels(actionName, "$code").observe(elapsedMillis)
      requestDurationHistogram.labels(actionName, "$code").observe(elapsedMillis)
      return result
    } catch (e: SocketTimeoutException) {
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDurationSummary.labels(actionName, "timeout").observe(elapsedMillis)
      requestDurationHistogram.labels(actionName, "timeout").observe(elapsedMillis)
      throw e
    } catch (e: Exception) {
      // Something else happened while the connection was in progress and we didn't receive
      // a complete response. We still want to record any long-running calls, however.
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDurationSummary.labels(actionName, "incomplete-response").observe(elapsedMillis)
      requestDurationHistogram.labels(actionName, "incomplete-response").observe(elapsedMillis)
      throw e
    }
  }

  private fun actionName(chain: Interceptor.Chain): String? {
    val invocation = chain.request().tag(Invocation::class.java)
    if (invocation != null) return "$clientName.${invocation.method().name}"

    val grpcMethod = chain.request().tag(GrpcMethod::class.java)
    if (grpcMethod != null) return "$clientName.${grpcMethod.path.substringAfterLast("/")}"

    val url = chain.request().tag(URL::class.java)
    if (url != null) return "$clientName.${url.path.trim('/').replace('/', '.')}"

    return null
  }

  private fun grpcStatusToHttpCode(grpcStatus: Int?): Int? {
    // This is copied from the armeria codebase at
    // https://github.com/line/armeria/blob/b9dc1ad1c6f4cfee8aba8e50a61d203c37eb94cc/grpc/src/main/java/com/linecorp/armeria/internal/common/grpc/GrpcStatus.java#L197-L236
    // which in turn is based off the google APIs at
    // https://github.com/googleapis/googleapis/blob/b2a7d2709887e38bcd3b5142424e563b0b386b6f/google/rpc/code.proto.
    return when (grpcStatus) {
      GrpcStatus.OK.code -> HTTP_OK
      GrpcStatus.CANCELLED.code -> HTTP_CLIENT_CLOSED_REQUEST
      GrpcStatus.UNKNOWN.code,
      GrpcStatus.INTERNAL.code,
      GrpcStatus.DATA_LOSS.code -> HTTP_INTERNAL_SERVER_ERROR
      GrpcStatus.INVALID_ARGUMENT.code,
      GrpcStatus.FAILED_PRECONDITION.code,
      GrpcStatus.OUT_OF_RANGE.code -> HTTP_BAD_REQUEST
      GrpcStatus.DEADLINE_EXCEEDED.code -> HTTP_GATEWAY_TIMEOUT
      GrpcStatus.NOT_FOUND.code -> HTTP_NOT_FOUND
      GrpcStatus.ALREADY_EXISTS.code,
      GrpcStatus.ABORTED.code -> HTTP_CONFLICT
      GrpcStatus.PERMISSION_DENIED.code -> HTTP_FORBIDDEN
      GrpcStatus.UNAUTHENTICATED.code -> HTTP_UNAUTHORIZED
      GrpcStatus.RESOURCE_EXHAUSTED.code -> HTTP_TOO_MANY_REQUESTS
      GrpcStatus.UNIMPLEMENTED.code -> HTTP_NOT_IMPLEMENTED
      GrpcStatus.UNAVAILABLE.code -> HTTP_SERVICE_UNAVAILABLE
      else -> null
    }
  }

  @Singleton
  class Factory @Inject internal constructor(
    m: Metrics,
    config: PrometheusConfig,
  ) {
    internal val requestDuration = m.summary(
      name = "client_http_request_latency_ms",
      help = "count and duration in ms of outgoing client requests",
      labelNames = listOf("action", "code"),
      maxAgeSeconds = config.max_age_in_seconds,
    )
    internal val requestDurationHistogram = m.histogram(
      name = "histo_client_http_request_latency_ms",
      help = "histogram in ms of outgoing client requests",
      labelNames = listOf("action", "code")
    )

    fun create(clientName: String) = ClientMetricsInterceptor(clientName, requestDuration, requestDurationHistogram)
  }
}

internal const val HTTP_OK = 200
internal const val HTTP_BAD_REQUEST = 400
internal const val HTTP_UNAUTHORIZED = 401
internal const val HTTP_FORBIDDEN = 403
internal const val HTTP_NOT_FOUND = 404
internal const val HTTP_CONFLICT = 409
internal const val HTTP_TOO_MANY_REQUESTS = 429
internal const val HTTP_CLIENT_CLOSED_REQUEST = 499
internal const val HTTP_INTERNAL_SERVER_ERROR = 500
internal const val HTTP_NOT_IMPLEMENTED = 501
internal const val HTTP_SERVICE_UNAVAILABLE = 503
internal const val HTTP_GATEWAY_TIMEOUT = 504
