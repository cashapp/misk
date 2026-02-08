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
 * `concurrency_feature_flag` and `parallelism_feature_flag` allow specifying LaunchDarkly feature flag names that
 * control concurrency and parallelism values per queue. When set, the flag is evaluated with the queue name as the key,
 * and if it returns a value > 0, that value overrides the YAML configuration. This allows dynamic configuration changes
 * with a service restart (without requiring a code deploy). If not set, only YAML configuration is used.
 */
data class SqsConfig
@JvmOverloads
constructor(
  val all_queues: SqsQueueConfig = SqsQueueConfig(),
  val per_queue_overrides: Map<String, SqsQueueConfig> = emptyMap(),
  val buffered_batch_flush_frequency_ms: Long = 50,
  /** Feature flag name for concurrency (e.g., "pod-jobqueue-consumers"). Evaluated with queue name as key. */
  val concurrency_feature_flag: String? = null,
  /** Feature flag name for parallelism. Evaluated with queue name as key. */
  val parallelism_feature_flag: String? = null,
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

  /** Returns true if any feature flags are configured. */
  fun hasFeatureFlags(): Boolean = concurrency_feature_flag != null || parallelism_feature_flag != null
}
