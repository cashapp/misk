package misk.aws2.sqs.jobqueue.config

import wisp.config.Config

/**
 * Sqs Configuration
 *
 * `default_config` will be applied to any queue that does not have its own configuration.
 * `per_queue_config` allows overriding configuration for a given queue
 */
data class SqsConfig @JvmOverloads constructor(
  val all_queues: SqsQueueConfig = SqsQueueConfig(),
  val per_queue_overrides: Map<String, SqsQueueConfig> = emptyMap(),
): Config
