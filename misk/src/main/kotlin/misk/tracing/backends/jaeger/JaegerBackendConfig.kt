package misk.tracing.backends.jaeger

import misk.config.Config

data class JaegerBackendConfig(
    val sampler: JaegerSamplerConfig?,
    val reporter: JaegerReporterConfig?
) : Config
