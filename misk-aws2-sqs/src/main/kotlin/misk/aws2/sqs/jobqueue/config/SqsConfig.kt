package misk.aws2.sqs.jobqueue.config

import misk.jobqueue.QueueName
import misk.config.Config

/**
 * Sqs Configuration
 *
 * `default_config` will be applied to any queue that does not have its own configuration.
 * `per_queue_config` allows overriding configuration for a given queue
 */
data class SqsConfig @JvmOverloads constructor(
  val all_queues: SqsQueueConfig = SqsQueueConfig(),
  val per_queue_overrides: Map<String, SqsQueueConfig> = emptyMap(),
): Config {
  /**
   * Returns resolved configuration for a given queue.
   */
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
}
