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
  fun `startUp with dynamic config completely replaces yaml config`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      config_feature_flag = "sqs-config-override",
    )

    // Dynamic config returns complete config that replaces YAML
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
  fun `startUp with dynamic config uses per_queue_overrides from dynamic config`() {
    val consumer = mock<SqsJobConsumer>()
    val handler1 = TestHandler()
    val handler2 = TestHandler()
    val handlers = mapOf(
      QueueName("queue-a") to handler1 as JobHandler,
      QueueName("queue-b") to handler2 as JobHandler,
    )
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 100, parallelism = 100), // YAML values ignored
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 100, parallelism = 100), // YAML values ignored
      ),
      config_feature_flag = "sqs-config-override",
    )

    // Dynamic config completely replaces YAML - YAML per_queue_overrides are NOT preserved
    val flagJsonString = """{
      "all_queues": {"concurrency": 1, "parallelism": 1},
      "per_queue_overrides": {
        "queue-a": {"concurrency": 15},
        "queue-b": {"concurrency": 8}
      }
    }"""
    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    // queue-a uses dynamic config's per_queue_override (concurrency=15, parallelism defaults to 1)
    verify(consumer).subscribe(
      eq(QueueName("queue-a")),
      eq(handler1),
      eq(SqsQueueConfig(concurrency = 15, parallelism = 1)),
    )
    // queue-b uses dynamic config's per_queue_override
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

  @Test
  fun `startUp with dynamic config does not inherit from yaml`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val dynamicConfig = mock<DynamicConfig>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 50, parallelism = 10, channel_capacity = 20),
      config_feature_flag = "sqs-config-override",
    )

    // Dynamic config only specifies concurrency - other fields use SqsQueueConfig defaults, NOT yaml values
    val flagJsonString = """{"all_queues": {"concurrency": 5}}"""
    whenever(dynamicConfig.getJsonString(eq(Feature("sqs-config-override")))).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(dynamicConfig), moshi)
    service.startAsync().awaitRunning()

    // parallelism and channel_capacity should be defaults (1 and 0), not YAML values (10 and 20)
    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 1, channel_capacity = 0)),
    )
  }

  private class TestHandler : SuspendingJobHandler {
    override suspend fun handleJob(job: Job): JobStatus = JobStatus.OK
  }
}
