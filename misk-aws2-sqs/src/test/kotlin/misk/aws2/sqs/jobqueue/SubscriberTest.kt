package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import io.prometheus.client.CollectorRegistry
import java.time.Clock
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.inject.AlwaysEnabledSwitch
import misk.jobqueue.QueueName
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
import org.mockito.kotlin.times
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
  fun `startDraining cancels the pending receive and polling stops without further receive attempts`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenReturn(pendingResponse)

    val subscriber = subscriber(handler = { JobStatus.OK })
    val pollingJob = backgroundScope.launch { subscriber.poll() }
    runCurrent()

    subscriber.startDraining()
    runCurrent()

    assertTrue(pendingResponse.isCancelled)
    assertTrue(pollingJob.isCompleted)
    verify(client, times(1)).receiveMessage(any<ReceiveMessageRequest>())
    assertEquals(0.0, sqsMetrics.receiveAttemptsAfterDrain.labels(queueName.value).get())
  }

  @Test
  fun `jobs already handed off are processed and acknowledged during drain`() = runTest {
    whenever(client.deleteMessage(any<DeleteMessageRequest>()))
      .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))
    val handledJobs = mutableListOf<String>()
    val subscriber =
      subscriber(
        handler = { job ->
          handledJobs += job.id
          JobStatus.OK
        }
      )

    channel.send(job("job-1"))
    channel.send(job("job-2"))
    subscriber.startDraining()
    val handlerJob = backgroundScope.launch { subscriber.run() }
    channel.close()
    runCurrent()

    assertEquals(listOf("job-1", "job-2"), handledJobs)
    verify(client, times(2)).deleteMessage(any<DeleteMessageRequest>())
    assertTrue(handlerJob.isCompleted)
    assertEquals(2.0, sqsMetrics.drainJobsCompleted.labels(queueName.value, "ok").get())
    assertEquals(2.0, sqsMetrics.drainAcksSucceeded.labels(queueName.value).get())
  }

  @Test
  fun `acknowledgement failure during drain is recorded as a failure not a success`() = runTest {
    whenever(client.deleteMessage(any<DeleteMessageRequest>()))
      .thenReturn(
        CompletableFuture.failedFuture<DeleteMessageResponse>(
          SqsException.builder().message("delete failed").statusCode(500).build()
        )
      )
    val subscriber = subscriber(handler = { JobStatus.OK })

    channel.send(job("job-1"))
    subscriber.startDraining()
    val handlerJob = backgroundScope.launch { subscriber.run() }
    channel.close()
    runCurrent()

    assertTrue(handlerJob.isCompleted)
    assertEquals(1.0, sqsMetrics.jobsFailedToAcknowledge.labels(queueName.value).get())
    assertEquals(1.0, sqsMetrics.drainAckFailures.labels(queueName.value).get())
    assertEquals(0.0, sqsMetrics.drainAcksSucceeded.labels(queueName.value).get())
  }

  @Test
  fun `a receive issued concurrently with drain start is cancelled and recorded`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val subscriber = subscriber(handler = { JobStatus.OK })
    // Simulate the race where the drain flag is set after the polling loop's check but before the
    // receive is registered: the first receive call flips the drain flag itself.
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())).thenAnswer {
      subscriber.startDraining()
      pendingResponse
    }

    val pollingJob = backgroundScope.launch { subscriber.poll() }
    runCurrent()

    assertTrue(pendingResponse.isCancelled)
    assertTrue(pollingJob.isCompleted)
    assertEquals(1.0, sqsMetrics.receiveAttemptsAfterDrain.labels(queueName.value).get())
  }

  private fun subscriber(
    handler: JobHandler,
    queueConfig: SqsQueueConfig = SqsQueueConfig(install_retry_queue = false),
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
