package misk.web.requestdeadlines

import io.prometheus.client.Histogram
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.client.ClientAction
import misk.metrics.v2.Metrics
import misk.web.DispatchMechanism
import misk.web.mediatype.MediaTypes
import java.time.Duration

@Singleton
internal class RequestDeadlineMetrics @Inject internal constructor(metrics: Metrics) {
  // Unified deadline exceeded metric - histogram provides both count and time distribution
  val deadlineExceededTimeHistogram: Histogram = metrics.histogram(
    name = "deadline_exceeded_time_ms",
    help = "how much time has passed since the deadline when exceeded",
    labelNames = listOf("action", "direction", "enforced", "protocol")
  )

  // Comprehensive deadline observability metrics - histogram provides both count and distribution
  val deadlineDistributionHistogram: Histogram = metrics.histogram(
    name = "deadline_duration_ms",
    help = "Distribution of deadline durations and count of propagated deadlines",
    labelNames = listOf("action", "source", "protocol"),
    buckets = listOf(100.0, 500.0, 1000.0, 5000.0, 10000.0, 30000.0, 60000.0)
  )

  // Helper methods to encapsulate telemetry logic
  fun recordDeadlinePropagated(action: Action, timeout: Duration, timeoutSource: String) {
    val protocol = determineProtocol(action.dispatchMechanism)

    // Single histogram provides both count (_count) and distribution
    deadlineDistributionHistogram.labels(action.name, timeoutSource, protocol)
      .observe(timeout.toMillis().toDouble())
  }

  fun recordDeadlineExceeded(action: Action, direction: String, enforced: Boolean, timePassedSinceDeadlineMs: Long) {
    val protocol = determineProtocol(action.dispatchMechanism)
    deadlineExceededTimeHistogram.labels(action.name, direction, enforced.toString(), protocol)
      .observe(timePassedSinceDeadlineMs.toDouble())
  }

  fun recordOutboundDeadlineExceeded(clientAction: ClientAction, enforced: Boolean, request: okhttp3.Request, timePassedSinceDeadlineMs: Long) {
    val protocol = determineProtocolFromRequest(request)
    deadlineExceededTimeHistogram.labels(clientAction.name, "outbound", enforced.toString(), protocol)
      .observe(timePassedSinceDeadlineMs.toDouble())
  }

  fun recordOutboundDeadlinePropagated(clientAction: ClientAction, deadlineMs: Long, request: okhttp3.Request) {
    val protocol = determineProtocolFromRequest(request)
    deadlineDistributionHistogram.labels(clientAction.name, "upstream_deadline", protocol)
      .observe(deadlineMs.toDouble())
  }


  private fun determineProtocol(dispatchMechanism: DispatchMechanism): String {
    return when (dispatchMechanism) {
      DispatchMechanism.GRPC -> "grpc"
      else -> "http"
    }
  }

  private fun determineProtocolFromRequest(request: okhttp3.Request): String {
    return if (request.header("Content-Type")?.startsWith(MediaTypes.APPLICATION_GRPC) == true) {
      "grpc"
    } else {
      "http"
    }
  }

  internal object SourceLabel {
    const val GRPC_TIMEOUT = "grpc_timeout"
    const val X_REQUEST_DEADLINE = "x_request_deadline"
    const val ENVOY_HEADER = "envoy_header"
    const val ACTION_DEFAULT = "action_default"
    const val GLOBAL_DEFAULT = "global_default"
  }

}
