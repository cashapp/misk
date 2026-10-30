package misk.aws2.sqs.jobqueue.coordinated

import com.google.inject.Provider
import com.squareup.moshi.Moshi
import io.prometheus.client.CollectorRegistry
import java.time.Clock
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.feature.testing.FakeFeatureFlags
import misk.jobqueue.Job
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.tasks.Status
import misk.testing.ConcurrentMockTracer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse

class SqsJobConsumerShutdownTest {
  private val queueName = QueueName("test-queue")
  private val client = mock<SqsClient>()
  private val featureFlags = FakeFeatureFlags().apply { override(SqsJobConsumer.CONSUMERS_BATCH_SIZE, 10) }
  private val queues =
    mock<QueueResolver> {
      on { getForReceiving(queueName) } doReturn
        ResolvedQueue(
          name = queueName,
          sqsQueueName = queueName,
          url = "https://sqs.us-west-2.amazonaws.com/123456789/test-queue",
          region = AwsRegion("us-west-2"),
          accountId = AwsAccountId("123456789"),
          client = client,
          batchManager = mock(),
          appName = "test-app",
          featureFlags = featureFlags,
        )
    }
  private val handledJobs = mutableListOf<String>()
  private val handler =
    object : JobHandler {
      override fun handleJob(job: Job) {
        synchronized(handledJobs) { handledJobs += job.id }
      }
    }
  private val executors = mutableListOf<ExecutorService>()

  @AfterEach
  fun tearDown() {
    executors.forEach { it.shutdownNow() }
  }

  @Test
  fun `jobs returned by a receive in flight at shutdown are released`() {
    val handlingThreads = newExecutor()
    val receiver = newReceiver(handlingThreads)
    val receiveStarted = CountDownLatch(1)
    val finishReceive = CountDownLatch(1)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())) doAnswer
      {
        receiveStarted.countDown()
        finishReceive.await()
        receiveResponse("job-1", "job-2")
      }
    stubChangeVisibility()

    val result = CompletableFuture.supplyAsync({ receiver.receive() }, newExecutor())
    assertTrue(receiveStarted.await(5, TimeUnit.SECONDS))
    receiver.stop()
    handlingThreads.shutdown()
    finishReceive.countDown()

    assertEquals(listOf(Status.NO_WORK, Status.NO_WORK), result.get(5, TimeUnit.SECONDS))
    assertEquals(emptyList(), handledJobs)
    assertEquals(mapOf("receipt-job-1" to 0, "receipt-job-2" to 0), visibilityChanges(times = 2))
  }

  @Test
  fun `jobs accepted before the handler pool shut down still run`() {
    val receiver = newReceiver(AcceptingThenRejectingExecutor(accepted = 1))
    receiver.stop()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())) doReturn receiveResponse("job-1", "job-2")
    stubChangeVisibility()

    assertEquals(listOf(Status.OK, Status.NO_WORK), receiver.receive())
    assertEquals(listOf("job-1"), handledJobs)
    assertEquals(mapOf("receipt-job-2" to 0), visibilityChanges(times = 1))
  }

  @Test
  fun `a failed release does not skip the remaining jobs`() {
    val handlingThreads = newExecutor().apply { shutdown() }
    val receiver = newReceiver(handlingThreads)
    receiver.stop()
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())) doReturn receiveResponse("job-1", "job-2")
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>()))
      .doThrow(SdkClientException.create("connection pool shut down"))
      .doReturn(ChangeMessageVisibilityResponse.builder().build())

    assertEquals(listOf(Status.NO_WORK, Status.NO_WORK), receiver.receive())
    assertEquals(mapOf("receipt-job-1" to 0, "receipt-job-2" to 0), visibilityChanges(times = 2))
  }

  @Test
  fun `a rejection while the receiver is running still propagates`() {
    val handlingThreads = newExecutor().apply { shutdown() }
    val receiver = newReceiver(handlingThreads)
    whenever(client.receiveMessage(any<ReceiveMessageRequest>())) doReturn receiveResponse("job-1")

    assertFailsWith<RejectedExecutionException> { receiver.receive() }
    verify(client, never()).changeMessageVisibility(any<ChangeMessageVisibilityRequest>())
  }

  @Suppress("DEPRECATION")
  private fun newReceiver(handlingThreads: ExecutorService): SqsJobConsumer.IndividualQueueReceiver {
    val consumer =
      SqsJobConsumer(
        handlingThreads = handlingThreads,
        taskQueue = mock(),
        receivingThreads = newExecutor(),
        sqsConsumerAllocator = mock(),
        featureFlags = featureFlags,
        metrics =
          SqsMetrics(
            misk.metrics.Metrics.factory(
              object : misk.metrics.v2.Metrics {
                override fun getRegistry() = CollectorRegistry()
              }
            )
          ),
        moshi = Moshi.Builder().build(),
        queues = queues,
        serviceManagerProvider = Provider { mock() },
        tracer = ConcurrentMockTracer(),
        clock = Clock.systemUTC(),
        awsSqsJobQueueConfig = AwsSqsJobQueueConfig(),
        asyncSwitch = mock(),
      )
    return consumer.IndividualQueueReceiver(queueName, handler)
  }

  private fun newExecutor(): ExecutorService = Executors.newFixedThreadPool(2).also { executors += it }

  private fun receiveResponse(vararg ids: String): ReceiveMessageResponse =
    ReceiveMessageResponse.builder()
      .messages(ids.map { Message.builder().messageId(it).receiptHandle("receipt-$it").body("{}").build() })
      .build()

  private fun stubChangeVisibility() {
    whenever(client.changeMessageVisibility(any<ChangeMessageVisibilityRequest>())) doReturn
      ChangeMessageVisibilityResponse.builder().build()
  }

  private fun visibilityChanges(times: Int): Map<String, Int> {
    val captor = argumentCaptor<ChangeMessageVisibilityRequest>()
    verify(client, times(times)).changeMessageVisibility(captor.capture())
    return captor.allValues.associate { it.receiptHandle() to it.visibilityTimeout() }
  }

  /** Runs the first [accepted] tasks inline, then rejects the rest as a terminated pool would. */
  private class AcceptingThenRejectingExecutor(private val accepted: Int) : AbstractExecutorService() {
    private val submitted = AtomicInteger()

    override fun execute(command: Runnable) {
      if (submitted.incrementAndGet() > accepted) throw RejectedExecutionException("pool terminated")
      command.run()
    }

    override fun shutdown() {}

    override fun shutdownNow(): List<Runnable> = emptyList()

    override fun isShutdown() = false

    override fun isTerminated() = false

    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
  }
}
