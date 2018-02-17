package misk.metrics.backends.stackdriver

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.monitoring.v3.Monitoring
import com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest
import com.google.api.services.monitoring.v3.model.Empty
import com.google.api.services.monitoring.v3.model.TimeSeries
import misk.logging.getLogger
import javax.inject.Inject

internal class StackDriverBatchedSender @Inject internal constructor(
    val monitoring: Monitoring,
    val config: StackDriverBackendConfig
) : StackDriverSender {
  companion object {
    val logger = getLogger<StackDriverSender>()
  }

  val timeSeriesApi = monitoring.projects()
      .timeSeries()

  override fun send(timeSeries: List<TimeSeries>) {
    if (timeSeries.size > config.batch_size) {
      val batch = monitoring.batch()
      timeSeries
          .chunked(config.batch_size)
          .map { CreateTimeSeriesRequest().setTimeSeries(it) }
          .forEach {
            timeSeriesApi.create(config.project_id, it)
                .queue(batch, ResponseCallback())
          }
      batch.execute()
    } else {
      val request = CreateTimeSeriesRequest().setTimeSeries(timeSeries)
      val create = timeSeriesApi.create(config.project_id, request)
          .execute()
      if (!create.isEmpty()) {
        logger.warn(create.toPrettyString())
      }
    }
  }

  class ResponseCallback : JsonBatchCallback<Empty>() {
    override fun onFailure(
        e: GoogleJsonError?,
        responseHeaders: HttpHeaders?
    ) {
      logger.warn("failed to send timeseries to stack driver ${e?.toPrettyString()}")
    }

    override fun onSuccess(
        t: Empty?,
        responseHeaders: HttpHeaders?
    ) {
    }
  }

}
