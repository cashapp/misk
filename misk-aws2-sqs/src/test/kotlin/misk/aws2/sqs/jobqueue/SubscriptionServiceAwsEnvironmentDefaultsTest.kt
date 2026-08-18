package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.ServiceManager
import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Tests that AWS environment defaults are applied to YAML config when region is not specified.
 */
@MiskTest(startService = false)
class SubscriptionServiceAwsEnvironmentDefaultsTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(
    dockerSqs = dockerSqs,
    // YAML config without region - should be populated from AWS environment
    yamlConfig = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 3, parallelism = 2),
    ),
  )

  @Inject private lateinit var subscriptionService: SubscriptionService
  @Inject private lateinit var serviceManager: ServiceManager

  @AfterEach
  fun tearDown() {
    if (::serviceManager.isInitialized) {
      serviceManager.stopAsync().awaitStopped()
    }
  }

  @Test
  fun `startUp with yaml config without region uses AWS environment default`() {
    // FakeAwsEnvironmentModule provides REGION="us-east-1"
    serviceManager.startAsync().awaitHealthy()

    val queueConfig = subscriptionService.effectiveConfig.getQueueConfig(QueueName("test-queue"))
    assertEquals(3, queueConfig.concurrency)
    assertEquals(2, queueConfig.parallelism)
    // Verify AWS environment default was applied for region
    assertEquals("us-east-1", queueConfig.region)
  }
}
