package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import java.util.Optional
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import misk.logging.getLogger

@Singleton
class SubscriptionService
@Inject
constructor(
  private val consumer: SqsJobConsumer,
  private val handlers: Map<QueueName, JobHandler>,
  private val config: SqsConfig,
  private val featureFlags: Optional<FeatureFlags>,
) : AbstractIdleService() {
  override fun startUp() {
    // Validate: if feature flags are configured, FeatureFlags must be bound
    if (config.hasFeatureFlags() && featureFlags.isEmpty) {
      throw IllegalStateException(
        "Feature flag names are configured in SqsConfig (concurrency_feature_flag=${config.concurrency_feature_flag}, " +
          "parallelism_feature_flag=${config.parallelism_feature_flag}) but no FeatureFlags implementation is bound. " +
          "Either bind a FeatureFlags implementation or remove the feature flag configuration from SqsConfig."
      )
    }

    logger.info { "Starting AWS SQS SubscriptionService with config=$config" }
    handlers.forEach { (queueName, handler) ->
      val queueConfig = config.getQueueConfig(queueName).applyFeatureFlags(
        featureFlags = featureFlags.orElse(null),
        queueName = queueName,
        concurrencyFlag = config.concurrency_feature_flag,
        parallelismFlag = config.parallelism_feature_flag,
      )
      logger.info { "Subscribing to queue ${queueName.value} with config: concurrency=${queueConfig.concurrency}, parallelism=${queueConfig.parallelism}" }
      consumer.subscribe(queueName, handler, queueConfig)
    }
  }

  override fun shutDown() {}

  companion object {
    private val logger = getLogger<SubscriptionService>()
  }
}

/**
 * Applies feature flag overrides to this queue config.
 *
 * If a feature flag name is provided and returns a value > 0 for the given queue name,
 * that value overrides the corresponding config value. Otherwise, the original config value is used.
 */
private fun SqsQueueConfig.applyFeatureFlags(
  featureFlags: FeatureFlags?,
  queueName: QueueName,
  concurrencyFlag: String?,
  parallelismFlag: String?,
): SqsQueueConfig {
  if (featureFlags == null) return this

  var result = this

  concurrencyFlag?.let { flag ->
    val value = featureFlags.getInt(Feature(flag), queueName.value)
    if (value > 0) result = result.copy(concurrency = value)
  }

  parallelismFlag?.let { flag ->
    val value = featureFlags.getInt(Feature(flag), queueName.value)
    if (value > 0) result = result.copy(parallelism = value)
  }

  return result
}
