package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

/**
 * Tests that YAML config values are used when no dynamic config override is present.
 */
@MiskTest(startService = true)
class SubscriptionServiceNoOverrideTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(dockerSqs)

  @Inject private lateinit var subscriptionService: SubscriptionService

  @Test
  fun `startUp without dynamic config override uses yaml config values`() {
    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(5, queueConfig.concurrency)
    assertEquals(2, queueConfig.parallelism)
  }
}
