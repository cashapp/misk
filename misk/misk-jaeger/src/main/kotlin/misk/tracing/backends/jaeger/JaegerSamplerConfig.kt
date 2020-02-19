package misk.tracing.backends.jaeger

import io.jaegertracing.Configuration
import misk.config.Config

/**
 * Configuration for Jaeger's sampler. If values are left null then Jaeger will provide defaults.
 * See [Configuration.SamplerConfiguration] for default values.
 */
data class JaegerSamplerConfig(
  val type: String?,
  val param: Number?,
  val manager_host_port: String?
) : Config
