package misk.tracing.backends.jaeger

import misk.config.Config

data class JaegerSamplerConfig(
  val type: String?,
  val param: Number?,
  val manager_host_port: String?
) : Config
