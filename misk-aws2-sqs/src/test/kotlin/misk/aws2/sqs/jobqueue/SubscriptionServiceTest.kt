package misk.aws2.sqs.jobqueue

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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionServiceTest {

  @Test
  fun `startUp without feature flags uses yaml config values`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
    )

    val service = SubscriptionService(consumer, handlers, config, Optional.empty())
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with feature flags overrides yaml config values`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      concurrency_feature_flag = "pod-jobqueue-consumers",
      parallelism_feature_flag = "pod-jobqueue-parallelism",
    )

    whenever(featureFlags.getInt(Feature("pod-jobqueue-consumers"), "test-queue")).thenReturn(10)
    whenever(featureFlags.getInt(Feature("pod-jobqueue-parallelism"), "test-queue")).thenReturn(3)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags))
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 10, parallelism = 3)),
    )
  }

  @Test
  fun `startUp with feature flag returning zero uses yaml fallback`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
      concurrency_feature_flag = "pod-jobqueue-consumers",
      parallelism_feature_flag = "pod-jobqueue-parallelism",
    )

    whenever(featureFlags.getInt(Feature("pod-jobqueue-consumers"), "test-queue")).thenReturn(0)
    whenever(featureFlags.getInt(Feature("pod-jobqueue-parallelism"), "test-queue")).thenReturn(0)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags))
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 5, parallelism = 2)),
    )
  }

  @Test
  fun `startUp with only concurrency flag configured`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val featureFlags = mock<FeatureFlags>()
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      concurrency_feature_flag = "pod-jobqueue-consumers",
    )

    whenever(featureFlags.getInt(Feature("pod-jobqueue-consumers"), "test-queue")).thenReturn(20)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags))
    service.startAsync().awaitRunning()

    verify(consumer).subscribe(
      eq(QueueName("test-queue")),
      eq(handler),
      eq(SqsQueueConfig(concurrency = 20, parallelism = 1)),
    )
  }

  @Test
  fun `startUp fails when feature flags configured but FeatureFlags not bound`() {
    val consumer = mock<SqsJobConsumer>()
    val handler = TestHandler()
    val handlers = mapOf(QueueName("test-queue") to handler as JobHandler)
    val config = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      concurrency_feature_flag = "pod-jobqueue-consumers",
    )

    val service = SubscriptionService(consumer, handlers, config, Optional.empty())

    val exception = assertFailsWith<IllegalStateException> {
      service.startAsync().awaitRunning()
    }

    // The exception is wrapped by Guava's Service, so check the cause
    val cause = exception.cause
    assertTrue(cause is IllegalStateException)
    assertEquals(
      "Feature flag names are configured in SqsConfig (concurrency_feature_flag=pod-jobqueue-consumers, " +
        "parallelism_feature_flag=null) but no FeatureFlags implementation is bound. " +
        "Either bind a FeatureFlags implementation or remove the feature flag configuration from SqsConfig.",
      cause.message,
    )
  }

  @Test
  fun `startUp with per-queue overrides and feature flags`() {
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
      concurrency_feature_flag = "pod-jobqueue-consumers",
    )

    // Feature flag overrides queue-a's yaml value, queue-b uses default
    whenever(featureFlags.getInt(Feature("pod-jobqueue-consumers"), "queue-a")).thenReturn(15)
    whenever(featureFlags.getInt(Feature("pod-jobqueue-consumers"), "queue-b")).thenReturn(8)

    val service = SubscriptionService(consumer, handlers, config, Optional.of(featureFlags))
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

  private class TestHandler : SuspendingJobHandler {
    override suspend fun handleJob(job: Job): JobStatus = JobStatus.OK
  }
}
