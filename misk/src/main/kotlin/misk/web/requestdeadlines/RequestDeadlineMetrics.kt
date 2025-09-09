package misk.web.requestdeadlines

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.client.ClientAction
import misk.metrics.v2.Metrics
import misk.web.DispatchMechanism
import java.time.Duration

@Singleton
internal class RequestDeadlineMetrics @Inject internal constructor(metrics: Metrics) {
  val deadlineExceededTimeHistogram: Histogram = metrics.histogram(
    name = "deadline_exceeded_time_ms",
    help = "how much time has passed since the deadline when exceeded",
    labelNames = listOf("action", "direction", "enforced", "protocol")
  )

  val deadlineDistributionHistogram: Histogram = metrics.histogram(
    name = "deadline_duration_ms",
    help = "Distribution of deadline durations and count of propagated deadlines",
    labelNames = listOf("action", "source", "protocol"),
    buckets = listOf(100.1, 500.1, 1000.1, 2000.1, 3000.1, 4000.1, 5000.1, 6000.1, 7000.1, 8000.1, 9000.1, 10000.1, 15000.1, 20000.1, 30000.1, 60000.1)
  )

  val noDeadlineInScopeCounter: Counter = metrics.counter(
    name = "deadline_not_in_scope_total",
    help = "Count of outbound requests that do not have a deadline in ActionScope",
    labelNames = listOf("action", "protocol")
  )

  fun recordDeadlinePropagated(action: Action, timeout: Duration, timeoutSource: String) {
    // Skip recording deadline metrics for healthcheck action
    if (action.isHealthCheckAction()) return

    val protocol = determineProtocol(action.dispatchMechanism)
    deadlineDistributionHistogram.labels(action.name, timeoutSource, protocol)
      .observe(timeout.toMillis().toDouble())
  }

  fun recordDeadlineExceeded(action: Action, direction: String, enforced: Boolean, timePassedSinceDeadlineMs: Long) {
    // Skip recording deadline metrics for healthcheck action
    if (action.isHealthCheckAction()) return

    val protocol = determineProtocol(action.dispatchMechanism)
    deadlineExceededTimeHistogram.labels(action.name, direction, enforced.toString(), protocol)
      .observe(timePassedSinceDeadlineMs.toDouble())
  }

  fun recordOutboundDeadlineExceeded(clientAction: ClientAction, enforced: Boolean, isGrpc: Boolean, timePassedSinceDeadline: Duration) {
    val protocol = if (isGrpc) "grpc" else "http"
    deadlineExceededTimeHistogram.labels(clientAction.name, "outbound", enforced.toString(), protocol)
      .observe(timePassedSinceDeadline.toMillis().toDouble())
  }

  fun recordOutboundDeadlinePropagated(clientAction: ClientAction, deadline: Duration, isGrpc: Boolean, source: String) {
    val protocol = if (isGrpc) "grpc" else "http"
    deadlineDistributionHistogram.labels(clientAction.name, source, protocol)
      .observe(deadline.toMillis().toDouble())
  }

  fun recordNoDeadlineInScope(clientAction: ClientAction, isGrpc: Boolean) {
    val protocol = if (isGrpc) "grpc" else "http"
    noDeadlineInScopeCounter.labels(clientAction.name, protocol)
      .inc()
  }

  private fun determineProtocol(dispatchMechanism: DispatchMechanism): String {
    return when (dispatchMechanism) {
      DispatchMechanism.GRPC -> "grpc"
      else -> "http"
    }
  }

  private fun Action.isHealthCheckAction(): Boolean {
    return name.lowercase() in HEALTH_CHECK_ACTIONS
  }

  companion object {
    private val HEALTH_CHECK_ACTIONS = setOf("livenesscheckaction", "readinesscheckaction")
  }

  internal object SourceLabel {
    const val GRPC_TIMEOUT = "grpc_timeout"
    const val X_REQUEST_DEADLINE = "x_request_deadline"
    const val ENVOY_HEADER = "envoy_header"
    const val ACTION_DEFAULT = "action_default"
    const val GLOBAL_DEFAULT = "global_default"
    const val PROPAGATED_DEADLINE = "propagated_deadline"
    const val OKHTTP_TIMEOUT = "okhttp_timeout"
  }

}
