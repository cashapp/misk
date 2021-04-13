package misk.config

import misk.web.WebConfig
import misk.web.exceptions.ActionExceptionLogLevelConfig
import wisp.config.Config
import java.time.Duration

data class TestConfig(
  val web: WebConfig,
  val consumer_a: ConsumerConfig,
  val consumer_b: ConsumerConfig,
  val duration: DurationConfig,
  val nested: NestedConfig,
  val action_exception_log_level: ActionExceptionLogLevelConfig
) : Config

data class ConsumerConfig(val min_items: Int = 0, val max_items: Int) : Config
data class DurationConfig(val interval: Duration) : Config

data class NestedConfig(val child_nested: ChildNestedConfig) : Config
data class ChildNestedConfig(val nested_value: String) : Config
