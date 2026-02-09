package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import java.util.Optional
import misk.aws2.sqs.jobqueue.config.SqsConfig
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
  private val moshi: Moshi,
) : AbstractIdleService() {
  override fun startUp() {
    // Validate: if feature flag is configured, FeatureFlags must be bound
    if (config.hasFeatureFlag() && featureFlags.isEmpty) {
      throw IllegalStateException(
        "Feature flag name is configured in SqsConfig (config_feature_flag=${config.config_feature_flag}) " +
          "but no FeatureFlags implementation is bound. " +
          "Either bind a FeatureFlags implementation or remove the feature flag configuration from SqsConfig."
      )
    }

    // Merge YAML config with feature flag config (if configured)
    val effectiveConfig = resolveEffectiveConfig()

    logger.info { "Starting AWS SQS SubscriptionService with config=$effectiveConfig" }
    handlers.forEach { (queueName, handler) ->
      val queueConfig = effectiveConfig.getQueueConfig(queueName)
      logger.info { "Subscribing to queue ${queueName.value} with config: concurrency=${queueConfig.concurrency}, parallelism=${queueConfig.parallelism}" }
      consumer.subscribe(queueName, handler, queueConfig)
    }
  }

  /**
   * Resolves the effective configuration by merging YAML config with feature flag overrides.
   * Feature flag values take precedence over YAML values.
   */
  private fun resolveEffectiveConfig(): SqsConfig {
    val flagName = config.config_feature_flag ?: return config
    val flags = featureFlags.orElse(null) ?: return config

    return try {
      val jsonAdapter = moshi.adapter(SqsConfig::class.java)
      val flagJsonString = flags.getJsonString(Feature(flagName), SERVICE_KEY)

      if (flagJsonString.isBlank() || flagJsonString == "null") {
        logger.info { "Feature flag '$flagName' returned empty/null, using YAML config only" }
        return config
      }

      val flagConfig = jsonAdapter.fromJson(flagJsonString)

      if (flagConfig == null) {
        logger.warn { "Failed to parse feature flag '$flagName' as SqsConfig, using YAML config only" }
        return config
      }

      logger.info { "Merging feature flag config from '$flagName': $flagConfig" }
      config.mergeWith(flagConfig)
    } catch (e: Exception) {
      logger.warn(e) { "Error reading feature flag '$flagName', using YAML config only" }
      config
    }
  }

  override fun shutDown() {}

  companion object {
    private val logger = getLogger<SubscriptionService>()
    /** Key used when evaluating the feature flag. Services can use this for targeting rules. */
    private const val SERVICE_KEY = "sqs-config"
  }
}
