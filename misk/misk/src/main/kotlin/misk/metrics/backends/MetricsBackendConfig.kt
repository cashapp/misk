package misk.metrics.backends

import misk.config.Config
import misk.metrics.backends.graphite.GraphiteBackendConfig
import misk.metrics.backends.signalfx.SignalFxBackendConfig
import misk.metrics.backends.stackdriver.StackDriverBackendConfig

data class MetricsBackendConfig(
    val graphite: GraphiteBackendConfig?,
    val stack_driver: StackDriverBackendConfig?,
    val signal_fx: SignalFxBackendConfig?
) : Config
