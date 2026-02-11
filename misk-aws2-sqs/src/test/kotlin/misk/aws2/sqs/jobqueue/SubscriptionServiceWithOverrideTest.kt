package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

/**
 * Tests that dynamic config completely replaces YAML config.
 */
@MiskTest(startService = true)
class SubscriptionServiceWithOverrideTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(
    dockerSqs = dockerSqs,
    dynamicConfigJson = """{"all_queues": {"concurrency": 10, "parallelism": 3, "region": "us-east-1"}}""",
  )

  @Inject private lateinit var subscriptionService: SubscriptionService

  @Test
  fun `startUp with dynamic config completely replaces yaml config`() {
    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(10, queueConfig.concurrency)
    assertEquals(3, queueConfig.parallelism)
  }
}
