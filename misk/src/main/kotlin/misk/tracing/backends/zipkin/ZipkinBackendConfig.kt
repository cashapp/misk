package misk.tracing.backends.zipkin

import misk.config.Config

data class ZipkinBackendConfig(
  val collector_url: String,
  val reporter: ZipkinReporterConfig?
) : Config
