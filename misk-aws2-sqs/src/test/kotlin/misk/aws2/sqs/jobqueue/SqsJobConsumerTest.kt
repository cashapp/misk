package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlinx.coroutines.delay
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.BlockingJobHandler
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import wisp.logging.getLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.random.Random
import kotlin.test.assertEquals

@MiskTest(startService = true)
class SqsJobConsumerTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module = SqsJobQueueTestModule(dockerSqs)

  @Inject private lateinit var jobConsumer: SqsJobConsumer

  @Test
  fun `everything produced is consumed, single queue, single handler per queue`() {
    val queueName = QueueName("test-queue-1")

    val result = createQueue(queueName)

    val latch = CountDownLatch(10)
    jobConsumer.subscribe(queueName, getHandler(latch))
    repeat(10) {
      sendMessage(result.queueUrl, "message")
    }

    latch.await(10, SECONDS)
    assertEquals(0, latch.count)
    jobConsumer.unsubscribe(queueName)
  }

  @Test
  fun `everything produced is consumed, single queue, multiple handlers per queue`() {
    val queueName = QueueName("test-queue-2")

    val result = createQueue(queueName)

    val latch = CountDownLatch(10)
    repeat(10) {
      sendMessage(result.queueUrl, "message")
    }

    jobConsumer.subscribe(queueName, getHandler(latch), SqsQueueConfig(
      parallelism = 1,
      concurrency = 1,
      channel_capacity = 0
    ))

    latch.await(10, SECONDS)
    assertEquals(0, latch.count)
  }

  @Test
  fun `everything produced is consumed, multiple queues, single handler per queue`() {
    val queueName1 = QueueName("test-queue-3")
    val queueName2 = QueueName("test-queue-4")

    val result1 = createQueue(queueName1)
    val result2 = createQueue(queueName2)

    val latch1 = CountDownLatch(10)
    val latch2 = CountDownLatch(10)
    for (r in listOf(result1, result2)) {
      repeat(10) {
        sendMessage(r.queueUrl, "message")
      }
    }

    jobConsumer.subscribe(queueName1, getHandler(latch1))
    jobConsumer.subscribe(queueName2, getHandler(latch2))

    latch1.await(10, SECONDS)
    assertEquals(0, latch1.count)
    latch2.await(10, SECONDS)
    assertEquals(0, latch2.count)
  }

  @Test
  fun `everything produced is consumed, multiple queues, multiple handlers per queue`() {
    val queueName1 = QueueName("test-queue-3")
    val queueName2 = QueueName("test-queue-4")

    val result1 = createQueue(queueName1)
    val result2 = createQueue(queueName2)

    val latch1 = CountDownLatch(20)
    val latch2 = CountDownLatch(20)
    for (r in listOf(result1, result2)) {
      repeat(20) {
        sendMessage(r.queueUrl, "message")
      }
    }

    jobConsumer.subscribe(queueName1, getHandler(latch1), SqsQueueConfig(
      parallelism = 1,
      concurrency = 3,
      channel_capacity = 0
    ))
    jobConsumer.subscribe(queueName2, getHandler(latch2), SqsQueueConfig(
      parallelism = 1,
      concurrency = 5,
      channel_capacity = 0
    ))

    latch1.await(10, SECONDS)
    assertEquals(0, latch1.count)
    latch2.await(10, SECONDS)
    assertEquals(0, latch2.count)
  }

  @Test
  fun `retrying works`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)
    val latch = CountDownLatch(1)
    sendMessage(result.queueUrl, "message")

    // Use explicit 2 seconds visibility timeout. After that messages should be reconsumed
    // and processed successfully. Intermittent issues handler will fail on the first processing
    jobConsumer.subscribe(queueName, getIntermittentIssuesHandler(latch), SqsQueueConfig(
      visibility_timeout = 2
    )
    )

    latch.await(10, SECONDS)
    assertEquals(0, latch.count)
  }

  @Test
  fun `retrying with delayed backoff works`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)
    val latch = CountDownLatch(3)
    sendMessage(result.queueUrl, "message")

    // Use explicit 1 seconds visibility timeout to start with
    // it should be increased exponentially and retried at most 3 times in 8 seconds
    jobConsumer.subscribe(
      queueName,
      getRetryWithBackoffHandler(latch),
      SqsQueueConfig(
        visibility_timeout = 1
      )
    )

    latch.await(8, SECONDS)
    assertEquals(0, latch.count)
  }

  @Test
  fun `queue is empty after success`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)
    val latch = CountDownLatch(1)
    sendMessage(result.queueUrl, "message")

    jobConsumer.subscribe(queueName, getHandler(latch))

    latch.await(5, SECONDS)
    assertEquals(0, latch.count)

    assertQueueSize(0, result.queueUrl)
  }

  @Test
  fun `move to deadletter`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)
    sendMessage(result.queueUrl, "message")

    val latch = CountDownLatch(1)
    jobConsumer.subscribe(queueName, getFailingHandler())

    latch.await(1, SECONDS)

    assertQueueSize(0, result.queueUrl)
    assertQueueSize(1, result.dlqQueueUrl)
  }

  @Test
  fun `consume from both regular and retry queue`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)

    val latch = CountDownLatch(2)
    jobConsumer.subscribe(queueName, getHandler(latch))

    sendMessage(result.queueUrl, "message")
    sendMessage(result.retryQueueUrl, "message")

    latch.await(10, SECONDS)
    assertEquals(0, latch.count, "Not all messages were consumed")

    assertQueueSize(0, result.queueUrl)
    assertQueueSize(0, result.retryQueueUrl)
  }

  @Test
  fun `don't consume from retry queue if it's not enabled`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)

    val latch = CountDownLatch(1)
    jobConsumer.subscribe(queueName, getHandler(latch), SqsQueueConfig(install_retry_queue = false))

    sendMessage(result.queueUrl, "message")
    sendMessage(result.retryQueueUrl, "message")

    latch.await(10, SECONDS)
    assertEquals(0, latch.count, "Not all messages were consumed")

    assertQueueSize(0, result.queueUrl)
    assertQueueSize(1, result.retryQueueUrl)
  }

  @Test
  fun `long running tests - simulates high traffic`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)

    // fill the queue a little before the subscribers is online
    repeat(10000) {
      sendMessage(result.queueUrl, "message $it")
    }

    val latch = CountDownLatch(20000)
    jobConsumer.subscribe(queueName, getDelayingHandler(latch), SqsQueueConfig(
      parallelism = 10,
      concurrency = 50,
      channel_capacity = 5
    ))

    // and push the subscriber a little bit more
    repeat(10000) {
      sendMessage(result.queueUrl, "message $it")
    }

    latch.await(30, SECONDS)
    assertEquals(0, latch.count, "Not all messages were consumed")
  }

  @Test
  fun `blocking handler`() {
    val queueName = QueueName("test-queue-1")
    val result = createQueue(queueName)

    val latch = CountDownLatch(10)
    jobConsumer.subscribe(queueName, object : BlockingJobHandler {
      override fun handleJob(job: Job): JobStatus {
        logger.info { "Handling job: body=${job.body} queue=${job.queueName.value}" }
        latch.countDown()
        return JobStatus.OK
      }
    })

    repeat(10) {
      sendMessage(result.queueUrl, "message $it")
    }

    latch.await(30, SECONDS)
    assertEquals(0, latch.count, "Not all messages were consumed")
  }

  private fun getDelayingHandler(latch: CountDownLatch): SuspendingJobHandler {
    return object : SuspendingJobHandler {
      override suspend fun handleJob(job: Job): JobStatus {
        val delay = Random.nextLong(30)
        logger.info { "Handling job with a delay=$delay: body=${job.body} queue=${job.queueName.value}" }
        latch.countDown()
        delay(delay)
        return JobStatus.OK
      }
    }
  }

  private fun assertQueueSize(expectedSize: Int, queueUrl: String) {
    val receivedMessages = DockerSqs.client.receiveMessage(
      ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .maxNumberOfMessages(10)
        .waitTimeSeconds(1)
        .build()
    ).join()
    assertEquals(expectedSize, receivedMessages.messages().size)
  }

  private fun createQueue(queueName: QueueName): CreatedQueues {
    val result = DockerSqs.client.createQueue(
      CreateQueueRequest.builder().queueName(queueName.value)
        .attributes(
          mapOf(
            QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
          ))
        .build()
    ).join()
    val retryResult = DockerSqs.client.createQueue(
      CreateQueueRequest.builder().queueName("${queueName.value}_retryq")
        .attributes(
          mapOf(
            QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
          ))
        .build()
    ).join()
    val dlqResult = DockerSqs.client.createQueue(
      CreateQueueRequest.builder().queueName("${queueName.value}_dlq")
        .attributes(
          mapOf(
            QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
          ))
        .build()
    ).join()
    return CreatedQueues(result.queueUrl(), retryResult.queueUrl(), dlqResult.queueUrl())
  }

  private fun sendMessage(queueUrl: String, message: String) {
    DockerSqs.client.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(message)
        .build()
    ).join()
  }

  private fun getHandler(latch: CountDownLatch): SuspendingJobHandler {
    return object : SuspendingJobHandler {
      override suspend fun handleJob(job: Job): JobStatus {
        logger.info { "Handling job: body=${job.body} queue=${job.queueName.value}" }
        latch.countDown()
        return JobStatus.OK
      }
    }
  }

  private fun getFailingHandler(): SuspendingJobHandler {
    return object : SuspendingJobHandler {
      override suspend fun handleJob(job: Job): JobStatus {
        logger.info { "Handling job: body=${job.body} queue=${job.queueName.value}" }
        return JobStatus.DEAD_LETTER
      }
    }
  }

  private fun getIntermittentIssuesHandler(latch: CountDownLatch): SuspendingJobHandler {
    return object : SuspendingJobHandler {
      private var counter = 1
      override suspend fun handleJob(job: Job): JobStatus {
        logger.info { "Handling job: body=${job.body} queue=${job.queueName.value}" }
        if (counter == 1) {
          counter -= 1
          return JobStatus.RETRY_LATER
        }
        latch.countDown()
        return JobStatus.OK
      }
    }
  }

  private fun getRetryWithBackoffHandler(latch: CountDownLatch): SuspendingJobHandler {
    return object : SuspendingJobHandler {
      override suspend fun handleJob(job: Job): JobStatus {
        logger.info { "Handling job: body=${job.body} queue=${job.queueName.value} ${job.attributes}" }
        latch.countDown()
        return JobStatus.RETRY_WITH_BACKOFF
      }
    }
  }

  data class CreatedQueues(
    val queueUrl: String,
    val retryQueueUrl: String,
    val dlqQueueUrl: String,
  )

  companion object {
    val logger = getLogger<SqsJobConsumerTest>()

  }
}
