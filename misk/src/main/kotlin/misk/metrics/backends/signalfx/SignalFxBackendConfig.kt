package misk.metrics.backends.signalfx

import misk.config.Config
import java.time.Duration

data class SignalFxBackendConfig(
    val access_token: String,
    val interval: Duration
) : Config
