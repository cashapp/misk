package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import io.prometheus.client.CollectorRegistry
import java.time.Clock
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.mock
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
  fun `status application failure does not stop subscriber`() = runTest {
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
    assertTrue(subscriberJob.isActive)
  }

  @Test
  fun `fetch failure does not stop polling`() = runTest {
    whenever(sqsQueueResolver.getQueueUrl(queueName)).thenReturn(queueUrl)
    val pendingResponse = CompletableFuture<ReceiveMessageResponse>()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>()))
      .thenReturn(CompletableFuture.failedFuture(IllegalStateException("fetch failed")))
      .thenReturn(
        CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message("job-1")).build())
      )
      .thenReturn(pendingResponse)

    val pollingJob = backgroundScope.launch { subscriber(handler = { JobStatus.OK }).poll() }
    runCurrent()

    assertEquals("job-1", channel.receive().id)
    assertTrue(pollingJob.isActive)
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
