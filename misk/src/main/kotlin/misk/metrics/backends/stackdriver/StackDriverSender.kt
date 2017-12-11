package misk.metrics.backends.stackdriver

import com.google.api.services.monitoring.v3.model.TimeSeries

internal interface StackDriverSender {
  fun send(timeSeries: List<TimeSeries>)
}
