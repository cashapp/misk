package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [SubscriptionService] configuration validation.
 *
 * These tests verify startup validation behavior without requiring Docker.
 * They use a mock [SqsJobConsumer] since we're only testing validation logic.
 */
class SubscriptionServiceConfigValidationTest {

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  @Test
  fun `startUp fails when dynamic config flag configured but DynamicConfig not bound`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      config_feature_flag = "sqs-config-override",
    )

    val service = SubscriptionService(consumer, handlers, config, Optional.empty(), moshi)

    val exception = assertFailsWith<IllegalStateException> {
      service.startAsync().awaitRunning()
    }

    // The exception is wrapped by Guava's Service, so check the cause
    val cause = exception.cause
    assertTrue(cause is IllegalStateException)
    assertEquals(
      "Dynamic config flag name is configured in SqsConfig (config_feature_flag=sqs-config-override) " +
        "but no DynamicConfig implementation is bound. " +
        "Either bind a DynamicConfig implementation or remove the feature flag configuration from SqsConfig.",
      cause.message,
    )
  }

  private class TestHandler : SuspendingJobHandler {
    override suspend fun handleJob(job: Job): JobStatus = JobStatus.OK
  }
}
