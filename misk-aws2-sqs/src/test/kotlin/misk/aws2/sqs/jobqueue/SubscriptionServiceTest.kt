package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.feature.DynamicConfig
import misk.feature.Feature
import misk.jobqueue.QueueName
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionServiceTest {

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  @Test
  fun `startUp without dynamic config uses yaml config values`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
    )

    val service = SubscriptionService(consumer, handlers, config, Optional.empty(), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with dynamic config overrides yaml config values`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      config_feature_flag = "sqs-config-override",
    )

    // Dynamic config returns JSON string with overrides
    val flagJsonString = """{"all_queues": {"concurrency": 10, "parallelism": 3}}"""
    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 10, parallelism = 3)),
    )
  }

  @Test
  fun `startUp with dynamic config returning empty uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      config_feature_flag = "sqs-config-override",
    )

    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn("")

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with dynamic config returning null string uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      config_feature_flag = "sqs-config-override",
    )

    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn("null")

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with dynamic config overriding only concurrency`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 5),
      config_feature_flag = "sqs-config-override",
    )

    // Dynamic config only overrides concurrency, parallelism should remain from YAML
    val flagJsonString = """{"all_queues": {"concurrency": 20}}"""
    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 20, parallelism = 5)),
    )
  }

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

  @Test
  fun `startUp with per-queue overrides from dynamic config`() {
    val consumer = mock<SqsJobConsumer>()
    val handler1 = TestHandler()
    val handler2 = TestHandler()
    val handlers = mapOf(
      QueueName("queue-a") to handler1 as JobHandler,
      QueueName("queue-b") to handler2 as JobHandler,
    )
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 5, parallelism = 2),
      ),
      config_feature_flag = "sqs-config-override",
    )

    // Dynamic config overrides queue-a's concurrency and adds queue-b override
    val flagJsonString = """{
      "per_queue_overrides": {
        "queue-a": {"concurrency": 15},
        "queue-b": {"concurrency": 8}
      }
    }"""
    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("queue-a")),
      eq(handler1),
      eq(SqsQueueConfig(concurrency = 15, parallelism = 2)),
    )
    verify(consumer).subscribe(
      eq(QueueName("queue-b")),
      eq(handler2),
      eq(SqsQueueConfig(concurrency = 8, parallelism = 1)),
    )
  }

  @Test
  fun `startUp with dynamic config error uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      config_feature_flag = "sqs-config-override",
    )

    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override"))))
      .thenThrow(RuntimeException("Dynamic config service unavailable"))

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    // Should fall back to YAML config when dynamic config throws
    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  private class TestHandler : SuspendingJobHandler {
    override suspend fun handleJob(job: Job): JobStatus = JobStatus.OK
  }
}
