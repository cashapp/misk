package misk.aws2.sqs.jobqueue.config

import misk.config.Config
import misk.jobqueue.QueueName

/**
 * Sqs Configuration
 *
 * `default_config` will be applied to any queue that does not have its own configuration. `per_queue_config` allows
 * overriding configuration for a given queue `buffered_batch_flush_frequency_ms` controls how often buffered messages
 * are flushed to SQS when using enqueueBuffered
 *
 * `config_feature_flag` allows specifying a dynamic config name that returns a JSON object matching the
 * structure of SqsConfig. When set, the dynamic config is evaluated at service startup and **completely replaces**
 * the YAML configuration. This allows dynamic configuration changes with a service restart (without requiring a code
 * deploy). If not set, or if the dynamic config returns null/empty, the YAML configuration is used.
 */
data class SqsConfig
@JvmOverloads
constructor(
  val all_queues: SqsQueueConfig = SqsQueueConfig(),
  val per_queue_overrides: Map<String, SqsQueueConfig> = emptyMap(),
  val buffered_batch_flush_frequency_ms: Long = 50,
  /**
   * Dynamic config name that returns a JSON object matching SqsConfig structure.
   * When set and returns a valid config, it completely replaces the YAML config.
   * Example value: {"all_queues": {"concurrency": 10}, "per_queue_overrides": {"my_queue": {"concurrency": 20}}}
   */
  val config_feature_flag: String? = null,
) : Config {
  /** Returns resolved configuration for a given queue. */
  fun getQueueConfig(queueName: QueueName): SqsQueueConfig {
    return if (per_queue_overrides[queueName.value] != null) {
      val override = per_queue_overrides[queueName.value]!!
      override.copy(
        wait_timeout = override.wait_timeout ?: all_queues.wait_timeout,
        visibility_timeout = override.visibility_timeout ?: all_queues.visibility_timeout,
        region = override.region ?: all_queues.region,
        account_id = override.account_id ?: all_queues.account_id,
      )
    } else {
      all_queues
    }
  }

  /** Returns true if a dynamic config flag is configured. */
  fun hasFeatureFlag(): Boolean = config_feature_flag != null
}
