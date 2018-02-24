package misk.tracing.backends.jaeger

import misk.config.Config

data class JaegerReporterConfig(
    val log_spans: Boolean?,
    val agent_host: String?,
    val agent_port: Int?,
    val flush_interval_ms: Int?,
    val max_queue_size: Int?
) : Config
