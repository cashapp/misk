package misk.tracing

import misk.config.Config

data class TestTracingConfig(
  val tracing: TracingConfig
) : Config
