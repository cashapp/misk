package misk.metrics.web

import misk.metrics.Metrics
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/** Exposes metrics as JSON over HTTP */
@Singleton
class MetricsJsonAction @Inject internal constructor(val metrics: Metrics) : WebAction {
  @Get("/_metrics")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun getMetrics(): JsonMetrics = JsonMetrics(metrics)
}
