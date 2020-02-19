package misk.tracing.backends.jaeger

import io.jaegertracing.Configuration
import misk.config.Config

/**
 * Configuration for Jaeger's reporter. If values are left null then Jaeger will provide defaults.
 * See [Configuration.ReporterConfiguration] for default values.
 */
data class JaegerReporterConfig(
  val log_spans: Boolean?,
  val agent_host: String?,
  val agent_port: Int?,
  val flush_interval_ms: Int?,
  val max_queue_size: Int?
) : Config
