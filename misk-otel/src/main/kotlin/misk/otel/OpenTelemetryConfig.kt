package misk.otel

import io.opentelemetry.api.common.Attributes
import java.time.Duration

data class OpenTelemetryConfig(
  val service_name: String,
  val service_version: String? = null,
  val otlp_endpoint: String? = null,
  val metric_export_interval: Duration = Duration.ofMinutes(1),
  val additional_attributes: Attributes = Attributes.empty()
)
