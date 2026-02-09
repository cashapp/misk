package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.jobqueue.QueueName
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionServiceTest {

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  @Test
  fun `startUp without feature flag uses yaml config values`() {
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
  fun `startUp with feature flag overrides yaml config values`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      config_feature_flag = "sqs-config-override",
    )

    // Feature flag returns JSON string with overrides
    val flagJsonString = """{"all_queues": {"concurrency": 10, "parallelism": 3}}"""
    whenever(featureFlags.getJsonString(eq(Feature("sqs-config-override")), any())).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 10, parallelism = 3)),
    )
  }

  @Test
  fun `startUp with feature flag returning empty uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      config_feature_flag = "sqs-config-override",
    )

    whenever(featureFlags.getJsonString(eq(Feature("sqs-config-override")), any())).thenReturn("")

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with feature flag returning null string uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      config_feature_flag = "sqs-config-override",
    )

    whenever(featureFlags.getJsonString(eq(Feature("sqs-config-override")), any())).thenReturn("null")

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with feature flag overriding only concurrency`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 5),
      config_feature_flag = "sqs-config-override",
    )

    // Feature flag only overrides concurrency, parallelism should remain from YAML
    val flagJsonString = """{"all_queues": {"concurrency": 20}}"""
    whenever(featureFlags.getJsonString(eq(Feature("sqs-config-override")), any())).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags), moshi)
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 20, parallelism = 5)),
    )
  }

  @Test
  fun `startUp fails when feature flag configured but FeatureFlags not bound`() {
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
      "Feature flag name is configured in SqsConfig (config_feature_flag=sqs-config-override) " +
        "but no FeatureFlags implementation is bound. " +
        "Either bind a FeatureFlags implementation or remove the feature flag configuration from SqsConfig.",
      cause.message,
    )
  }

  @Test
  fun `startUp with per-queue overrides from feature flag`() {
    val consumer = mock<SqsJobConsumer>()
    val handler1 = TestHandler()
    val handler2 = TestHandler()
    val handlers = mapOf(
      QueueName("queue-a") to handler1 as JobHandler,
      QueueName("queue-b") to handler2 as JobHandler,
    )
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 5, parallelism = 2),
      ),
      config_feature_flag = "sqs-config-override",
    )

    // Feature flag overrides queue-a's concurrency and adds queue-b override
    val flagJsonString = """{
      "per_queue_overrides": {
        "queue-a": {"concurrency": 15},
        "queue-b": {"concurrency": 8}
      }
    }"""
    whenever(featureFlags.getJsonString(eq(Feature("sqs-config-override")), any())).thenReturn(flagJsonString)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags), moshi)
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
  fun `startUp with feature flag error uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      config_feature_flag = "sqs-config-override",
    )

    whenever(featureFlags.getJsonString(eq(Feature("sqs-config-override")), any()))
      .thenThrow(RuntimeException("Feature flag service unavailable"))

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags), moshi)
    service.startAsync().awaitRunning()

    // Should fall back to YAML config when feature flag throws
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
