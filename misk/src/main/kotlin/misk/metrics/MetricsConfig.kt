package misk.metrics

import misk.config.Config
import misk.metrics.backends.MetricsBackendConfig

data class MetricsConfig(val backends: MetricsBackendConfig) : Config

