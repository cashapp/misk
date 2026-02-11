package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

/**
 * Tests that per_queue_overrides from dynamic config are respected.
 */
@MiskTest(startService = true)
class SubscriptionServicePerQueueOverrideTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(
    dockerSqs = dockerSqs,
    yamlConfig = SqsConfig(
      // YAML values should be ignored when dynamic config is present
      all_queues = SqsQueueConfig(concurrency = 100, parallelism = 100, region = "us-east-1"),
      config_feature_flag = "test-sqs-config",
    ),
    dynamicConfigJson = """{
      "all_queues": {"concurrency": 1, "parallelism": 1, "region": "us-east-1"},
      "per_queue_overrides": {
        "test-queue": {"concurrency": 15}
      }
    }""",
  )

  @Inject private lateinit var subscriptionService: SubscriptionService

  @Test
  fun `startUp with dynamic config uses per_queue_overrides from dynamic config`() {
    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(15, queueConfig.concurrency)
    assertEquals(1, queueConfig.parallelism)
  }
}
