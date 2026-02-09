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
 * structure of [SqsConfigOverride]. When set, the dynamic config is evaluated at service startup and merged with the
 * YAML configuration (dynamic config values take precedence). This allows dynamic configuration changes with a service
 * restart (without requiring a code deploy). If not set, only YAML configuration is used.
 */
data class SqsConfig
@JvmOverloads
constructor(
  val all_queues: SqsQueueConfig = SqsQueueConfig(),
  val per_queue_overrides: Map<String, SqsQueueConfig> = emptyMap(),
  val buffered_batch_flush_frequency_ms: Long = 50,
  /**
   * Dynamic config name that returns a JSON object matching [SqsConfigOverride] structure.
   * When set, the dynamic config value is merged with YAML config (dynamic config values take precedence).
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

  /**
   * Applies an override to this config. Only non-null fields in the override are applied.
   */
  fun applyOverride(override: SqsConfigOverride): SqsConfig {
    val mergedPerQueueOverrides = per_queue_overrides.toMutableMap()

    // Apply per-queue overrides from the dynamic config
    override.per_queue_overrides?.forEach { (queueName, queueOverride) ->
      val existingConfig = mergedPerQueueOverrides[queueName] ?: all_queues
      mergedPerQueueOverrides[queueName] = existingConfig.applyOverride(queueOverride)
    }

    return copy(
      all_queues = override.all_queues?.let { all_queues.applyOverride(it) } ?: all_queues,
      per_queue_overrides = mergedPerQueueOverrides,
      buffered_batch_flush_frequency_ms = override.buffered_batch_flush_frequency_ms ?: buffered_batch_flush_frequency_ms,
    )
  }
}

/**
 * Override configuration for SQS, used by dynamic config.
 *
 * All fields are nullable - null means "use the base config value", while any non-null value
 * will override the base config. This allows dynamic config to explicitly set values back to
 * defaults if needed.
 */
data class SqsConfigOverride
@JvmOverloads
constructor(
  val all_queues: SqsQueueConfigOverride? = null,
  val per_queue_overrides: Map<String, SqsQueueConfigOverride>? = null,
  val buffered_batch_flush_frequency_ms: Long? = null,
)
