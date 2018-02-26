package misk.tracing

import misk.config.Config
import misk.tracing.backends.TracingBackendConfig

data class TracingConfig(val backends: TracingBackendConfig) : Config
