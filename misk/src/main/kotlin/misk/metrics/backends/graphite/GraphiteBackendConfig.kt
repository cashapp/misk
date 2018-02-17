package misk.metrics.backends.graphite

import misk.config.Config
import java.time.Duration

data class GraphiteBackendConfig(
    val host: String,
    val port: Int,
    val interval: Duration
) : Config
