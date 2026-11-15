package misk.aws2.sqs.jobqueue

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import java.util.Optional
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.cloud.aws.AwsRegion
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import misk.logging.getLogger
import misk.moshi.adapter

@Singleton
class SubscriptionService
@Inject
constructor(
  private val consumer: SqsJobConsumer,
  private val handlers: Map<QueueName, JobHandler>,
  private val config: SqsConfig,
  private val maybeDynamicConfig: Optional<DynamicConfig>,
  private val moshi: Moshi,
  private val awsRegion: AwsRegion,
) : AbstractIdleService() {

  /** The effective config after resolving dynamic config overrides. Available after startup. */
  @VisibleForTesting
  lateinit var effectiveConfig: SqsConfig
    private set

  override fun startUp() {
    // Validate: if dynamic config flag is configured, DynamicConfig must be bound
    if (config.hasFeatureFlag() && maybeDynamicConfig.isEmpty) {
      throw IllegalStateException(
        "Dynamic config flag name is configured in SqsConfig (config_feature_flag=${config.config_feature_flag}) " +
          "but no DynamicConfig implementation is bound. " +
          "Either bind a DynamicConfig implementation or remove the feature flag configuration from SqsConfig."
      )
    }

    // Use dynamic config if available, otherwise fall back to YAML config
    effectiveConfig = resolveEffectiveConfig()

    logger.info { "Starting AWS SQS SubscriptionService with config=$effectiveConfig" }
    handlers.forEach { (queueName, handler) ->
      val queueConfig = effectiveConfig.getQueueConfig(queueName)
      logger.info { "Subscribing to queue ${queueName.value} with config: concurrency=${queueConfig.concurrency}, parallelism=${queueConfig.parallelism}" }
      consumer.subscribe(queueName, handler, queueConfig)
    }
  }

  /**
   * Resolves the effective configuration.
   * If a dynamic config flag is configured and returns a valid config, it completely replaces the YAML config.
   * Otherwise, the YAML config is used.
   *
   * In both cases, if region is not specified in the config, it is populated from the AWS environment.
   */
  private fun resolveEffectiveConfig(): SqsConfig {
    val flagName = config.config_feature_flag ?: return applyRegionDefault(config)
    val dynamicConfig = maybeDynamicConfig.orElse(null) ?: return applyRegionDefault(config)

    return try {
      val jsonAdapter = moshi.adapter<SqsConfig>()
      val flagJsonString = dynamicConfig.getJsonString(Feature(flagName))

      if (flagJsonString.isBlank() || flagJsonString == "null") {
        logger.info { "Dynamic config '$flagName' returned empty/null, using YAML config" }
        return applyRegionDefault(config)
      }

      val dynamicSqsConfig = jsonAdapter.fromJson(flagJsonString)

      if (dynamicSqsConfig == null) {
        logger.warn { "Failed to parse dynamic config '$flagName' as SqsConfig, using YAML config" }
        return applyRegionDefault(config)
      }

      logger.info { "Using dynamic config from '$flagName' (replaces YAML config): $dynamicSqsConfig" }
      applyRegionDefault(dynamicSqsConfig)
    } catch (e: Exception) {
      logger.warn(e) { "Error reading dynamic config '$flagName', using YAML config" }
      applyRegionDefault(config)
    }
  }

  /**
   * Applies the AWS region default to the config if not already specified.
   * This ensures dynamic config behaves the same as YAML config with respect to region auto-population.
   */
  private fun applyRegionDefault(sqsConfig: SqsConfig): SqsConfig {
    return if (sqsConfig.all_queues.region == null) {
      sqsConfig.copy(all_queues = sqsConfig.all_queues.copy(region = awsRegion.name))
    } else {
      sqsConfig
    }
  }

  override fun shutDown() {}

  companion object {
    private val logger = getLogger<SubscriptionService>()
  }
}
