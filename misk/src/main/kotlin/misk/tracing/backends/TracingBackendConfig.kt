package misk.tracing.backends

import misk.config.Config
import misk.tracing.backends.jaeger.JaegerBackendConfig
import misk.tracing.backends.zipkin.ZipkinBackendConfig

data class TracingBackendConfig(
  val jaeger: JaegerBackendConfig? = null,
  val zipkin: ZipkinBackendConfig? = null
) : Config
