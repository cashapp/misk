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
  val collection: List<CollectionItem>,
  val action_exception_log_level: ActionExceptionLogLevelConfig
) : Config

data class ConsumerConfig(val min_items: Int = 0, val max_items: Int) : Config
data class DurationConfig(val interval: Duration) : Config

data class CollectionItem(val name: String, val optional: Int?) : Config

data class NestedConfig(val child_nested: ChildNestedConfig) : Config
data class ChildNestedConfig(val nested_value: String) : Config

data class EnvironmentTestConfig(
  val string_value: String,
  val ignored_classpath_value: String,
  val int_value: Int,
  val long_value: Long,
  val float_value: Float,
  val boolean_value: Boolean,
  val default_string: String,
  val default_int: Int,
  val default_boolean: Boolean,
  val secret_api_key: Secret<String>,
  val secret_int: Secret<Int>,
  val secret_long: Secret<Long>,
  val secret_float: Secret<Float>,
  // URL defaults with colons - these test the fix for the original issue
  val http_url_default: String,
  val jdbc_url_default: String,
  val https_url_with_port_default: String,
) : Config
