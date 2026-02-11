package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.ServiceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import jakarta.inject.Inject
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**

Tests that startup fails when config_feature_flag is configured but DynamicConfig is not bound.
 */
@MiskTest(startService = false)
class SubscriptionServiceConfigValidationTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val queueCreator = SubscriptionServiceTestQueueCreator(dockerSqs)

  @MiskTestModule
  private val module = SubscriptionServiceTestModule(
    dockerSqs = dockerSqs,
    installFakeFeatureFlags = false
  )

  @Inject private lateinit var serviceManager: ServiceManager

  @Test
  fun `startUp fails when dynamic config flag configured but DynamicConfig not bound`() {
    val exception = assertFailsWith<IllegalStateException> {
      serviceManager.startAsync().awaitHealthy()
    }

    val cause = exception.suppressedExceptions.firstOrNull()?.cause
    assertTrue(cause is IllegalStateException)
    assertEquals(
      "Dynamic config flag name is configured in SqsConfig (config_feature_flag=test-sqs-config) " +
        "but no DynamicConfig implementation is bound. " +
        "Either bind a DynamicConfig implementation or remove the feature flag configuration from SqsConfig.",
      cause.message,
    )
  }
}
