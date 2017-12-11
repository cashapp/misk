package misk.metrics.web

import misk.metrics.Metrics
import misk.web.Get
import misk.web.JsonResponseBody
import misk.web.actions.WebAction
import javax.inject.Inject
import javax.inject.Singleton

/** Exposes metrics as JSON over HTTP */
@Singleton
class MetricsJsonAction @Inject internal constructor(val metrics: Metrics) : WebAction {
  @JsonResponseBody
  @Get("/_metrics")
  fun getMetrics(): JsonMetrics = JsonMetrics(metrics)
}
