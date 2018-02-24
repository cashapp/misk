package misk.tracing.backends

import misk.config.Config
import misk.tracing.backends.jaeger.JaegerBackendConfig

data class TracingBackendConfig(
    val jaeger: JaegerBackendConfig?
) : Config
