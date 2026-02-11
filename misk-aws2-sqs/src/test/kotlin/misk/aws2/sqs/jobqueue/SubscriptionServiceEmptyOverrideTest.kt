package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

/**
 * Tests that empty dynamic config falls back to YAML config.
 */
@MiskTest(startService = true)
class SubscriptionServiceEmptyOverrideTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(
    dockerSqs = dockerSqs,
    dynamicConfigJson = "",
  )

  @Inject private lateinit var subscriptionService: SubscriptionService

  @Test
  fun `startUp with dynamic config returning empty uses yaml fallback`() {
    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(5, queueConfig.concurrency)
    assertEquals(2, queueConfig.parallelism)
  }
}
