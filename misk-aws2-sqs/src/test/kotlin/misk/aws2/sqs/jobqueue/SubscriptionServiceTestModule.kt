package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.feature.Feature
import misk.feature.testing.FakeFeatureFlagsModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.testing.ExternalDependency
import misk.testing.MockTracingBackendModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

/**
 * Reusable test module for [SubscriptionService] tests.
 *
 * @param dockerSqs The Docker SQS instance
 * @param yamlConfig The YAML-based SqsConfig (always required as fallback)
 * @param dynamicConfigJson Optional JSON string to override via dynamic config flag.
 *        If null, no flag override is set. If empty string or "null", tests fallback behavior.
 */
class SubscriptionServiceTestModule(
  private val dockerSqs: DockerSqs,
  private val yamlConfig: SqsConfig = SqsConfig(
    all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2, region = "us-east-1"),
    config_feature_flag = "test-sqs-config",
  ),
  private val dynamicConfigJson: String? = null,
  private val installFakeFeatureFlags: Boolean = true,
) : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(MockTracingBackendModule())

    if (installFakeFeatureFlags) {
      // Install FakeFeatureFlagsModule with optional override
      if (dynamicConfigJson != null) {
        install(
          FakeFeatureFlagsModule().withOverrides {
            overrideJsonString(Feature("test-sqs-config"), dynamicConfigJson)
          }
        )
      } else {
        install(FakeFeatureFlagsModule())
      }
    }

    install(AwsEnvironmentModule())
    install(FakeAwsEnvironmentModule())

    bind<AwsCredentialsProvider>().toInstance(dockerSqs.credentialsProvider)
    bind<Region>().toInstance(dockerSqs.region)

    install(SqsJobQueueModule(yamlConfig) { endpointOverride(dockerSqs.endpointUri) })
    install(SqsJobHandlerModule.create<SubscriptionServiceTestJobHandler>(QueueName("test-queue")))
  }
}

@Singleton
class SubscriptionServiceTestJobHandler @Inject constructor() : SuspendingJobHandler {
  override suspend fun handleJob(job: Job): JobStatus = JobStatus.OK
}

class SubscriptionServiceTestQueueCreator(private val dockerSqs: DockerSqs) : ExternalDependency {
  private val queues = listOf("test-queue", "test-queue_retryq", "test-queue_dlq")

  override fun startup() {}
  override fun shutdown() {}

  override fun beforeEach() {
    queues.forEach {
      dockerSqs.client
        .createQueue(
          CreateQueueRequest.builder()
            .queueName(it)
            .attributes(
              mapOf(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "1",
                QueueAttributeName.VISIBILITY_TIMEOUT to "5",
              )
            )
            .build()
        )
        .join()
    }
  }

  override fun afterEach() {}
}
