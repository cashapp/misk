package misk.metrics.backends.stackdriver

import misk.config.Config
import java.time.Duration

data class StackDriverBackendConfig(
    val project_id: String,
    val interval: Duration,
    val batch_size: Int = 200
) : Config
