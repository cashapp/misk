package misk.cloud.aws.logging

import ch.qos.logback.classic.Level
import misk.config.Config
import java.time.Duration

data class CloudwatchLogConfig(
  val event_buffer_size: Int = 1024 * 1024,
  val endpoint: String? = null,
  val max_flush_delay: Duration = Duration.ofSeconds(1),
  val max_batch_size: Int = 100,
  val filter_level: Level = Level.INFO
) : Config