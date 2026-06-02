package misk.web.interceptors

import com.squareup.wire.GrpcStatus
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.HttpURLConnection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.reflect.full.findAnnotation
import misk.Action
import misk.grpc.GrpcTimeoutMarshaller
import misk.web.DispatchMechanism
import misk.web.HttpCall
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RequestDeadlineMode
import misk.web.RequestDeadlineTimeout
import misk.web.RequestDeadlinesConfig
import misk.web.WebConfig
import misk.web.requestdeadlines.RequestDeadlineMetrics
import misk.web.requestdeadlines.RequestDeadlineMetrics.SourceLabel

internal class RequestDeadlineInterceptor
private constructor(
  private val clock: Clock,
  private val requestDeadlinesConfig: RequestDeadlinesConfig,
  private val metrics: RequestDeadlineMetrics,
  private val action: Action,
) : NetworkInterceptor {

  override fun intercept(chain: NetworkChain) {
    val httpCall = chain.httpCall

    // Skip deadline handling for WebSocket requests - they are long-lived connections
    // and should use idle timeouts instead of request deadlines
    if (action.dispatchMechanism == DispatchMechanism.WEBSOCKET) {
      chain.proceed(httpCall)
      return
    }

    val (timeout, timeoutSource) = determineTimeoutAndSource(action, httpCall, requestDeadlinesConfig)
    metrics.recordDeadlinePropagated(action, timeout, timeoutSource)

    val requestReceivedByJettyTimestamp: Instant = Instant.ofEpochMilli(httpCall.requestReceivedTimestamp)
    val deadline: Instant = requestReceivedByJettyTimestamp.plus(timeout)

    if (clock.instant().isAfter(deadline)) {
      val enforced =
        requestDeadlinesConfig.mode == RequestDeadlineMode.ENFORCE_INBOUND ||
          requestDeadlinesConfig.mode == RequestDeadlineMode.ENFORCE_ALL

      val timePassedSinceDeadlineMs = Duration.between(deadline, clock.instant()).toMillis()
      metrics.recordDeadlineExceeded(action, "inbound", enforced, timePassedSinceDeadlineMs)

      if (enforced) {
        // Already queued in Jetty for too long, no need to even proceed
        when (action.dispatchMechanism) {
          DispatchMechanism.GRPC -> {
            // For gRPC, set DEADLINE_EXCEEDED status code (4)
            httpCall.setResponseTrailer("grpc-status", GrpcStatus.DEADLINE_EXCEEDED.code.toString())
            httpCall.setResponseTrailer("grpc-message", DEADLINE_EXCEEDED_MESSAGE)
          }
          else -> {
            // For HTTP, use 504 Gateway Timeout
            httpCall.statusCode = HttpURLConnection.HTTP_GATEWAY_TIMEOUT
            httpCall.takeResponseBody()?.use { sink -> sink.writeUtf8(DEADLINE_EXCEEDED_MESSAGE) }
          }
        }
        return
      }
    }

    httpCall.computeRequestHeader(MISK_REQUEST_DEADLINE_HEADER) {
      Pair(
        MISK_REQUEST_DEADLINE_HEADER,
        deadline.toString(),
      ) // prints in UTC format, for e.g. 2024-03-26T23:13:48.123456789Z
    }
    chain.proceed(httpCall)
  }

  /**
   * Determines the timeout duration and its source for a given request, which is tracked in metrics.
   *
   * For gRPC requests, uses the grpc-timeout header if present and positive, otherwise falls back to defaults. For HTTP
   * requests, examines X-Request-Deadline (supports both seconds and ISO8601 duration format) and
   * x-envoy-expected-rq-timeout-ms headers, selecting the minimum positive value if multiple are present, otherwise
   * uses defaults.
   *
   * Priority order for fallback: action annotation timeout > global default timeout.
   *
   * @return Pair of (timeout duration, source label for metrics)
   */
  private fun determineTimeoutAndSource(
    action: Action,
    httpCall: HttpCall,
    requestDeadlinesConfig: RequestDeadlinesConfig,
  ): Pair<Duration, String> {
    val annotationTimeoutMs = action.function.findAnnotation<RequestDeadlineTimeout>()?.timeoutMs
    val fallbackTimeout = Duration.ofMillis(annotationTimeoutMs ?: requestDeadlinesConfig.global_timeout_ms)
    val fallbackSource = if (annotationTimeoutMs != null) SourceLabel.ACTION_DEFAULT else SourceLabel.GLOBAL_DEFAULT

    return when (action.dispatchMechanism) {
      DispatchMechanism.GRPC -> {
        val grpcTimeoutValue = httpCall.requestHeaders[GrpcTimeoutMarshaller.TIMEOUT_KEY]
        val grpcTimeoutNanos = grpcTimeoutValue?.let { GrpcTimeoutMarshaller.parseAsciiString(it) }?.takeIf { it > 0 }

        // "shadow" grpc headers used in METRICS_ONLY/PROPAGATE_ONLY mode to prevent GrpcClient actually hanging up on
        // grpc-timeout
        val shadowGrpcTimeoutValue = httpCall.requestHeaders[CUSTOM_GRPC_TIMEOUT_PROPAGATE_HEADER]
        val shadowGrpcTimeoutNanos =
          shadowGrpcTimeoutValue?.let { GrpcTimeoutMarshaller.parseAsciiString(it) }?.takeIf { it > 0 }

        // Prefer actual to shadow value, though only one of them should be set.
        val effectiveGrpcTimeout = grpcTimeoutNanos ?: shadowGrpcTimeoutNanos
        if (effectiveGrpcTimeout != null) {
          Duration.ofNanos(effectiveGrpcTimeout) to SourceLabel.GRPC_TIMEOUT
        } else {
          fallbackTimeout to fallbackSource
        }
      }
      else -> {
        // HTTP timeout logic with source tracking
        val xRequestDeadlineTimeout =
          parseXRequestDeadlineHeader(httpCall.requestHeaders[HTTP_HEADER_X_REQUEST_DEADLINE])
        val envoyTimeoutMs = httpCall.requestHeaders[HTTP_HEADER_ENVOY_DEADLINE]?.toLongOrNull()

        val validTimeouts = mutableListOf<Pair<Duration, String>>()
        xRequestDeadlineTimeout?.let { duration ->
          if (!duration.isNegative && !duration.isZero) validTimeouts.add(duration to SourceLabel.X_REQUEST_DEADLINE)
        }
        envoyTimeoutMs?.let { ms -> if (ms > 0) validTimeouts.add(Duration.ofMillis(ms) to SourceLabel.ENVOY_HEADER) }

        if (validTimeouts.isNotEmpty()) {
          validTimeouts.minBy { it.first }
        } else {
          fallbackTimeout to fallbackSource
        }
      }
    }
  }

  /**
   * Parses the X-Request-Deadline header which can be either:
   * 1. A number representing seconds
   * 2. An ISO8601 duration string (e.g., "PT30S", "PT5M", "PT1H30M") This is consistent with what JSC expects:
   *    https://github.com/squareup/java/blob/master/webservice/src/main/java/com/squareup/webservice/framework/HttpRequestDeadlines.java
   *
   * @param headerValue the raw header value
   * @return timeout as Duration, or null if invalid/unparseable
   */
  private fun parseXRequestDeadlineHeader(headerValue: String?): Duration? {
    if (headerValue.isNullOrBlank()) return null

    return try {
      // First try parsing as seconds (legacy format)
      val seconds = headerValue.toLongOrNull()
      if (seconds != null) {
        return Duration.ofSeconds(seconds)
      }

      // Try parsing as ISO8601 duration
      Duration.parse(headerValue)
    } catch (_: Exception) {
      // Invalid format, return null
      null
    }
  }

  companion object {
    const val HTTP_HEADER_X_REQUEST_DEADLINE = "x-request-deadline"
    const val HTTP_HEADER_ENVOY_DEADLINE = "x-envoy-expected-rq-timeout-ms" // milliseconds
    // custom header used in METRICS_ONLY/PROPAGATE_ONLY mode to propagate grpc-timeout value, to prevent
    // GrpcClient actually hanging up
    const val CUSTOM_GRPC_TIMEOUT_PROPAGATE_HEADER = "x-grpc-timeout-propagate"

    const val MISK_REQUEST_DEADLINE_HEADER = "Misk-Request-Deadline"

    const val DEADLINE_EXCEEDED_MESSAGE = "deadline exceeded: queued for too long"
  }

  @Singleton
  class Factory
  @Inject
  constructor(private val clock: Clock, private val webConfig: WebConfig, private val metrics: RequestDeadlineMetrics) :
    NetworkInterceptor.Factory {

    override fun create(action: Action) =
      RequestDeadlineInterceptor(
        clock = clock,
        requestDeadlinesConfig = webConfig.request_deadlines,
        metrics = metrics,
        action = action,
      )
  }
}
