package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.ServiceManager
import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.feature.Feature
import misk.feature.testing.FakeFeatureFlags
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Tests for [SubscriptionService] dynamic config override behavior.
 *
 * Uses `startService = false` to allow per-test configuration of dynamic config
 * values before the service starts up.
 */
@MiskTest(startService = false)
class SubscriptionServiceDynamicConfigTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(
    dockerSqs = dockerSqs,
    yamlConfig = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2, region = "us-east-1"),
      config_feature_flag = "test-sqs-config",
    ),
  )

  @Inject private lateinit var subscriptionService: SubscriptionService
  @Inject private lateinit var serviceManager: ServiceManager
  @Inject private lateinit var fakeFeatureFlags: FakeFeatureFlags

  @AfterEach
  fun tearDown() {
    if (::serviceManager.isInitialized) {
      serviceManager.stopAsync().awaitStopped()
    }
  }

  @Test
  fun `startUp without dynamic config override uses yaml config values`() {
    // No override set - should use YAML defaults
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(5, queueConfig.concurrency)
    assertEquals(2, queueConfig.parallelism)
  }

  @Test
  fun `startUp with dynamic config completely replaces yaml config`() {
    fakeFeatureFlags.overrideJsonString(
      Feature("test-sqs-config"),
      """{"all_queues": {"concurrency": 10, "parallelism": 3, "region": "us-east-1"}}""",
    )
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(10, queueConfig.concurrency)
    assertEquals(3, queueConfig.parallelism)
  }

  @Test
  fun `startUp with dynamic config returning empty uses yaml fallback`() {
    fakeFeatureFlags.overrideJsonString(Feature("test-sqs-config"), "")
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(5, queueConfig.concurrency)
    assertEquals(2, queueConfig.parallelism)
  }

  @Test
  fun `startUp with dynamic config returning null string uses yaml fallback`() {
    fakeFeatureFlags.overrideJsonString(Feature("test-sqs-config"), "null")
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(5, queueConfig.concurrency)
    assertEquals(2, queueConfig.parallelism)
  }

  @Test
  fun `startUp with dynamic config uses per_queue_overrides from dynamic config`() {
    fakeFeatureFlags.overrideJsonString(
      Feature("test-sqs-config"),
      """{
        "all_queues": {"concurrency": 1, "parallelism": 1, "region": "us-east-1"},
        "per_queue_overrides": {
          "test-queue": {"concurrency": 15}
        }
      }""",
    )
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(15, queueConfig.concurrency)
    assertEquals(1, queueConfig.parallelism)
  }

  @Test
  fun `startUp with dynamic config without region uses AWS environment default`() {
    // Dynamic config without region - should be populated from AWS environment
    // FakeAwsEnvironmentModule provides REGION="us-east-1"
    fakeFeatureFlags.overrideJsonString(
      Feature("test-sqs-config"),
      """{"all_queues": {"concurrency": 7, "parallelism": 4}}""",
    )
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(7, queueConfig.concurrency)
    assertEquals(4, queueConfig.parallelism)
    // Verify AWS environment default was applied for region
    assertEquals("us-east-1", queueConfig.region)
  }
}
