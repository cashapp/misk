package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import io.prometheus.client.CollectorRegistry
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.aws2.sqs.jobqueue.config.SqsVisibilityHeartbeatConfig
import misk.inject.AlwaysEnabledSwitch
import misk.jobqueue.QueueName
import misk.jobqueue.v2.BlockingJobHandler
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.metrics.v2.Metrics
import misk.testing.ConcurrentMockTracer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import software.amazon.awssdk.services.sqs.model.SqsException

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriberTest {
  private val queueName = QueueName("test-queue")
  private val queueUrl = "https://sqs.us-west-2.amazonaws.com/123456789/test-queue"
  private val channel = Channel<SqsJob>(Channel.UNLIMITED)
  private val client = mock<SqsAsyncClient>()
  private val sqsQueueResolver = mock<SqsQueueResolver>()
  private val moshi = Moshi.Builder().build()
  private val sqsMetrics =
    SqsMetrics(
      object : Metrics {
        override fun getRegistry() = CollectorRegistry()
      }
    )

  @Test
  fun `retry with backoff failure does not stop subscriber`() = runTest {
    val handledJobs = mutableListOf<String>()
    val statuses = ArrayDeque(listOf(JobStatus.RETRY_WITH_BACKOFF, JobStatus.OK))
    val handler =
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job): JobStatus {
          handledJobs += job.id
          return statuses.removeFirst()
        }
      }
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>()))
      .thenReturn(
        CompletableFuture.failedFuture<ChangeMessageVisibilityResponse>(
          SqsException.builder().message("ReceiptHandle is invalid").statusCode(400).build()
        )
      )
    whenever(client.deleteMessage(any<DeleteMessageRequest>()))
      .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

    val subscriberJob = backgroundScope.launch { subscriber(handler).run() }
    channel.send(job("job-1"))
    channel.send(job("job-2"))
    runCurrent()

    assertEquals(listOf("job-1", "job-2"), handledJobs)
    verify(client).changeMessageVisibility(any<ChangeMessageVisibilityRequest>())
    verify(client).deleteMessage(any<DeleteMessageRequest>())
    assertEquals(1.0, sqsMetrics.jobsFailedToRetryWithBackoff.labels(queueName.value).get())
    assertTrue(subscriberJob.isActive)
  }

  @Test
  fun `dead letter failure does not acknowledge job or stop subscriber`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName.deadLetterQueue)).thenReturn("$queueUrl-dlq")
    val handledJobs = mutableListOf<String>()
    val statuses = ArrayDeque(listOf(JobStatus.DEAD_LETTER, JobStatus.OK))
    val handler =
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job): JobStatus {
          handledJobs += job.id
          return statuses.removeFirst()
        }
      }
    whenever(client.sendMessage(any<SendMessageRequest>()))
      .thenReturn(
        CompletableFuture.failedFuture<SendMessageResponse>(
          SqsException.builder().message("send failed").statusCode(500).build()
        )
      )
    whenever(client.deleteMessage(any<DeleteMessageRequest>()))
      .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

    val subscriberJob = backgroundScope.launch { subscriber(handler).run() }
    channel.send(job("job-1"))
    channel.send(job("job-2"))
    runCurrent()

    assertEquals(listOf("job-1", "job-2"), handledJobs)
    verify(client).sendMessage(any<SendMessageRequest>())
    val deleteRequest = argumentCaptor<DeleteMessageRequest>()
    verify(client).deleteMessage(deleteRequest.capture())
    assertEquals("receipt-job-2", deleteRequest.firstValue.receiptHandle())
    assertEquals(1.0, sqsMetrics.jobsFailedToDeadLetter.labels(queueName.value).get())
    assertTrue(subscriberJob.isActive)
  }

  @Test
  fun `canceled fetch does not stop polling unless subscriber is canceled`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val canceledResponse = CompletableFuture<ReceiveMessageResponse>().apply { cancel(false) }
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>()))
      .thenReturn(canceledResponse)
      .thenReturn(
        CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message("job-1")).build())
      )
      .thenReturn(pendingResponse)

    val pollingJob = backgroundScope.launch { subscriber(handler = { JobStatus.OK }).poll() }
    runCurrent()

    assertEquals("job-1", channel.receive().id)
    assertTrue(pollingJob.isActive)
    assertEquals(1.0, sqsMetrics.sqsReceiveFailures.labels(queueName.value).get())

    pollingJob.cancelAndJoin()
    assertTrue(pollingJob.isCancelled)
  }

  @Test
  fun `stop lets an in-flight fetch finish`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenReturn(pendingResponse)

    val subscriber = subscriber(handler = { JobStatus.OK })
    val pollingJob = backgroundScope.launch { subscriber.poll() }
    runCurrent()

    subscriber.stop()
    runCurrent()
    assertTrue(pollingJob.isActive)

    // The fetch that was already in flight still delivers its messages.
    pendingResponse.complete(ReceiveMessageResponse.builder().messages(message("job-1")).build())
    runCurrent()

    assertEquals("job-1", channel.receive().id)
    assertTrue(pollingJob.isCompleted)
    assertTrue(channel.receiveCatching().isClosed)
    assertEquals(0.0, sqsMetrics.sqsReceiveFailures.labels(queueName.value).get())
  }

  @Test
  fun `canceling in-flight fetches ends polling and closes the channel`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenReturn(pendingResponse)

    val subscriber = subscriber(handler = { JobStatus.OK })
    val pollingJob = backgroundScope.launch { subscriber.poll() }
    runCurrent()
    assertTrue(pollingJob.isActive)

    subscriber.stop()
    subscriber.cancelInFlightReceives()
    runCurrent()

    assertTrue(pendingResponse.isCancelled)
    assertTrue(pollingJob.isCompleted)
    assertFalse(pollingJob.isCancelled)
    assertTrue(channel.receiveCatching().isClosed)
    // Canceling as part of a stop is expected, so it isn't counted as a receive failure.
    assertEquals(0.0, sqsMetrics.sqsReceiveFailures.labels(queueName.value).get())
  }

  @Test
  fun `a fetch started after cancellation is canceled immediately`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val subscriber = subscriber(handler = { JobStatus.OK })
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    // Stop the subscriber while it is issuing the request, so the set is swept before the future is registered.
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenAnswer {
      subscriber.stop()
      subscriber.cancelInFlightReceives()
      pendingResponse
    }

    val pollingJob = backgroundScope.launch { subscriber.poll() }
    runCurrent()

    assertTrue(pendingResponse.isCancelled)
    assertTrue(pollingJob.isCompleted)
    assertFalse(pollingJob.isCancelled)
  }

  @Test
  fun `renews every received message while handler is busy and stops on cancellation`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>()))
      .thenReturn(
        CompletableFuture.completedFuture(
          ReceiveMessageResponse.builder().messages((1..10).map { message("job-$it") }).build()
        )
      )
      .thenReturn(CompletableFuture())
    val renewed = mutableListOf<String>()
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>())).thenAnswer {
      val request = it.getArgument<ChangeMessageVisibilityRequest>(0)
      assertEquals(300, request.visibilityTimeout())
      renewed += request.receiptHandle()
      CompletableFuture.completedFuture(ChangeMessageVisibilityResponse.builder().build())
    }
    val subscriber =
      subscriber(
        object : SuspendingJobHandler {
          override suspend fun handleJob(job: Job): JobStatus = awaitCancellation()
        },
        SqsQueueConfig(
          install_retry_queue = false,
          visibility_timeout = 43_200,
          visibility_heartbeat = SqsVisibilityHeartbeatConfig(),
        ),
        channel = Channel(),
        timeSource = testScheduler.timeSource,
      )
    val polling = backgroundScope.launch { subscriber.poll() }
    val handling = backgroundScope.launch { subscriber.run() }
    runCurrent()
    val receive = argumentCaptor<ReceiveMessageRequest>()
    org.mockito.kotlin.verify(client, org.mockito.kotlin.atLeastOnce()).receiveMessage(receive.capture())
    assertEquals(300, receive.firstValue.visibilityTimeout())
    repeat(10) {
      advanceTimeBy(60_000)
      runCurrent()
    }
    assertEquals(100, renewed.size)
    assertEquals((1..10).map { "receipt-job-$it" }.toSet(), renewed.toSet())
    polling.cancelAndJoin()
    handling.cancelAndJoin()
    advanceTimeBy(300_000)
    runCurrent()
    assertEquals(100, renewed.size)
  }

  @Test
  fun `acknowledgement waits for renewal and prevents later renewals`() = runTest {
    val result = CompletableDeferred<JobStatus>()
    val renewal = CompletableFuture<ChangeMessageVisibilityResponse>()
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>())).thenReturn(renewal)
    whenever(client.deleteMessage(any<DeleteMessageRequest>()))
      .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))
    val subscriber = renewableSubscriber(testScheduler.timeSource) { result.await() }
    backgroundScope.launch { subscriber.poll() }
    backgroundScope.launch { subscriber.run() }
    runCurrent()
    advanceTimeBy(60_000)
    runCurrent()
    result.complete(JobStatus.OK)
    runCurrent()
    verify(client, never()).deleteMessage(any<DeleteMessageRequest>())
    assertFalse(renewal.isCancelled)
    renewal.complete(ChangeMessageVisibilityResponse.builder().build())
    runCurrent()
    verify(client).deleteMessage(any<DeleteMessageRequest>())
    advanceTimeBy(600_000)
    runCurrent()
    verify(client).changeMessageVisibility(any<ChangeMessageVisibilityRequest>())
  }

  @Test
  fun `renewal failure cancels processing without acknowledging`() = runTest {
    var canceled = false
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>()))
      .thenReturn(CompletableFuture.failedFuture(SqsException.builder().statusCode(500).build()))
    val subscriber =
      renewableSubscriber(testScheduler.timeSource) {
        try {
          awaitCancellation()
        } finally {
          canceled = true
        }
      }
    backgroundScope.launch { subscriber.poll() }
    backgroundScope.launch { subscriber.run() }
    runCurrent()
    advanceTimeBy(60_000)
    runCurrent()
    assertTrue(canceled)
    advanceTimeBy(600_000)
    runCurrent()
    verify(client).changeMessageVisibility(any<ChangeMessageVisibilityRequest>())
    verify(client, never()).deleteMessage(any<DeleteMessageRequest>())
  }

  @Test
  fun `processing deadline cancels a live hung handler`() = runTest {
    var canceled = false
    val subscriber =
      renewableSubscriber(testScheduler.timeSource, SqsVisibilityHeartbeatConfig(processing_timeout_ms = 30_000)) {
        try {
          awaitCancellation()
        } finally {
          canceled = true
        }
      }
    backgroundScope.launch { subscriber.poll() }
    backgroundScope.launch { subscriber.run() }
    runCurrent()
    advanceTimeBy(30_000)
    runCurrent()
    assertTrue(canceled)
    verify(client, never()).changeMessageVisibility(any<ChangeMessageVisibilityRequest>())
    verify(client, never()).deleteMessage(any<DeleteMessageRequest>())
  }

  @Test
  fun `renewals stay within twelve hours from receive including long polling time`() = runTest {
    var canceled = false
    var receiveStarted = 0L
    val received = CompletableFuture<ReceiveMessageResponse>()
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenReturn(received).thenReturn(CompletableFuture())
    var lastVisibility = 0
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>())).thenAnswer {
      val request = it.getArgument<ChangeMessageVisibilityRequest>(0)
      lastVisibility = request.visibilityTimeout()
      assertTrue(testScheduler.currentTime + lastVisibility * 1000L < receiveStarted + 43_200_000)
      CompletableFuture.completedFuture(ChangeMessageVisibilityResponse.builder().build())
    }
    val subscriber =
      subscriber(
        object : SuspendingJobHandler {
          override suspend fun handleJob(job: Job): JobStatus {
            try {
              awaitCancellation()
            } finally {
              canceled = true
            }
          }
        },
        SqsQueueConfig(
          install_retry_queue = false,
          visibility_heartbeat = SqsVisibilityHeartbeatConfig(processing_timeout_ms = 43_200_000),
        ),
        timeSource = testScheduler.timeSource,
      )
    backgroundScope.launch { subscriber.poll() }
    backgroundScope.launch { subscriber.run() }
    runCurrent()
    advanceTimeBy(20_000)
    received.complete(ReceiveMessageResponse.builder().messages(message("job-1")).build())
    runCurrent()
    advanceTimeBy(43_170_000)
    runCurrent()
    assertTrue(lastVisibility in 1..299)
    advanceTimeBy(10_000)
    runCurrent()
    assertTrue(canceled)
    verify(client, never()).deleteMessage(any<DeleteMessageRequest>())
  }

  @Test
  fun `worker loss lets a received message become visible again after its last renewal`() = runTest {
    var visibleAt = 0L
    var deliveries = 0
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenAnswer {
      if (testScheduler.currentTime < visibleAt) CompletableFuture<ReceiveMessageResponse>()
      else {
        deliveries++
        visibleAt = testScheduler.currentTime + it.getArgument<ReceiveMessageRequest>(0).visibilityTimeout() * 1000L
        CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message("job-1")).build())
      }
    }
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>())).thenAnswer {
      visibleAt =
        testScheduler.currentTime + it.getArgument<ChangeMessageVisibilityRequest>(0).visibilityTimeout() * 1000L
      CompletableFuture.completedFuture(ChangeMessageVisibilityResponse.builder().build())
    }
    fun consumer() =
      subscriber(
        object : SuspendingJobHandler {
          override suspend fun handleJob(job: Job): JobStatus = awaitCancellation()
        },
        SqsQueueConfig(install_retry_queue = false, visibility_heartbeat = SqsVisibilityHeartbeatConfig()),
        channel = Channel(),
        timeSource = testScheduler.timeSource,
      )
    val first = consumer()
    val poller = backgroundScope.launch { first.poll() }
    backgroundScope.launch { first.run() }
    runCurrent()
    advanceTimeBy(600_000)
    runCurrent()
    assertEquals(1, deliveries)
    poller.cancelAndJoin()
    assertEquals(900_000, visibleAt)
    advanceTimeBy(299_999)
    runCurrent()
    assertTrue(testScheduler.currentTime < visibleAt)
    advanceTimeBy(1)
    val second = consumer()
    backgroundScope.launch { second.poll() }
    backgroundScope.launch { second.run() }
    runCurrent()
    assertEquals(2, deliveries)
  }

  @Test
  fun `deadline interrupts blocking handler on an independent dispatcher`() = runTest {
    val started = CountDownLatch(1)
    val interrupted = CountDownLatch(1)
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>()))
      .thenReturn(
        CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message("job-1")).build())
      )
      .thenReturn(CompletableFuture())
    val subscriber =
      subscriber(
        object : BlockingJobHandler {
          override fun handleJob(job: Job): JobStatus {
            started.countDown()
            try {
              CountDownLatch(1).await()
              return JobStatus.OK
            } finally {
              interrupted.countDown()
            }
          }
        },
        SqsQueueConfig(
          install_retry_queue = false,
          visibility_heartbeat = SqsVisibilityHeartbeatConfig(processing_timeout_ms = 30_000),
        ),
        timeSource = testScheduler.timeSource,
      )
    backgroundScope.launch { subscriber.poll() }
    backgroundScope.launch(Dispatchers.IO) { subscriber.run() }
    runCurrent()
    assertTrue(started.await(5, TimeUnit.SECONDS))
    advanceTimeBy(30_000)
    runCurrent()
    assertTrue(interrupted.await(5, TimeUnit.SECONDS))
    verify(client, never()).deleteMessage(any<DeleteMessageRequest>())
  }

  @Test
  fun `stuck renewal is bounded and cancels handler`() = runTest {
    var canceled = false
    val renewal = CompletableFuture<ChangeMessageVisibilityResponse>()
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>())).thenReturn(renewal)
    val subscriber =
      renewableSubscriber(testScheduler.timeSource) {
        try {
          awaitCancellation()
        } finally {
          canceled = true
        }
      }
    backgroundScope.launch { subscriber.poll() }
    backgroundScope.launch { subscriber.run() }
    runCurrent()
    advanceTimeBy(70_000)
    runCurrent()
    assertTrue(canceled)
    assertTrue(renewal.isCancelled)
    verify(client, never()).deleteMessage(any<DeleteMessageRequest>())
  }

  private fun renewableSubscriber(
    timeSource: TimeSource,
    config: SqsVisibilityHeartbeatConfig = SqsVisibilityHeartbeatConfig(),
    handler: suspend (Job) -> JobStatus,
  ): Subscriber {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>()))
      .thenReturn(
        CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message("job-1")).build())
      )
      .thenReturn(CompletableFuture())
    return subscriber(
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job) = handler(job)
      },
      SqsQueueConfig(install_retry_queue = false, visibility_heartbeat = config),
      timeSource = timeSource,
    )
  }

  private fun subscriber(
    handler: JobHandler,
    queueConfig: SqsQueueConfig = SqsQueueConfig(install_retry_queue = false),
    channel: Channel<SqsJob> = this.channel,
    timeSource: TimeSource = TimeSource.Monotonic,
  ) =
    Subscriber(
      queueName = queueName,
      queueConfig = queueConfig,
      deadLetterQueueName = queueName.deadLetterQueue,
      handler = handler,
      channel = channel,
      client = client,
      sqsQueueResolver = sqsQueueResolver,
      sqsMetrics = sqsMetrics,
      moshi = moshi,
      clock = Clock.systemUTC(),
      tracer = ConcurrentMockTracer(),
      visibilityTimeoutCalculator = VisibilityTimeoutCalculator(),
      asyncSwitch = AlwaysEnabledSwitch(),
      timeSource = timeSource,
    )

  private fun subscriber(handler: suspend (Job) -> JobStatus): Subscriber =
    subscriber(
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job) = handler(job)
      }
    )

  private fun job(id: String) =
    SqsJob(
      queueName = queueName,
      moshi = moshi,
      message = message(id),
      queueUrl = queueUrl,
      publishToChannelTimestamp = Clock.systemUTC().millis(),
    )

  private fun message(id: String) =
    Message.builder()
      .messageId(id)
      .body("body-$id")
      .receiptHandle("receipt-$id")
      .attributes(mapOf(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT to "1"))
      .build()
}
