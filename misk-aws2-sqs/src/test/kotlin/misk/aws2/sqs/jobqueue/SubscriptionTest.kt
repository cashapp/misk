package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobEnqueuer
import misk.jobqueue.v2.JobHandler
import misk.testing.ExternalDependency
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@MiskTest(startService = true)
class SubscriptionTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  // creates queues before the tests start
  @MiskExternalDependency private val fakeQueueCreator = FakeQueueCreator(dockerSqs)

  private val sqsConfig = SqsConfig(
    per_queue_overrides = mapOf(
      "external-test-queue" to SqsQueueConfig(
        region = "us-west-2",
        account_id = "1234567890",
        install_retry_queue = false,
      )
    )
  )
  @MiskTestModule private val module = SqsJobHandlerTestModule(dockerSqs, sqsConfig)

  @Inject
  private lateinit var jobEnqueuer: SqsJobEnqueuer

  @Inject
  private lateinit var handlers: Map<QueueName, JobHandler>

  @Test
  fun `everything that is published via different APIs is consumed`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    jobEnqueuer.enqueue(
      queueName = queueName,
      body = "message_1",
      idempotencyKey = "idempotency_key_1",
      deliveryDelay = Duration.ofMillis(100),
      attributes = emptyMap(),
    )

    jobEnqueuer.enqueueBlocking(
      queueName = queueName,
      body = "message_2",
      idempotencyKey = "idempotency_key_2",
      deliveryDelay = Duration.ZERO,
      attributes = emptyMap()
    )

    jobEnqueuer.enqueueAsync(
      queueName = queueName,
      body = "message_3",
      idempotencyKey = "idempotency_key_3",
      deliveryDelay = Duration.ofSeconds(1),
      attributes = emptyMap()
    ).join()

    val latch = handler.counter
    latch.await()
    assertEquals(0, latch.count)

    val jobs = handler.jobs
    assertEquals(3, jobs.size)
  }

  @Test
  fun `external queues work`() = runTest {
    val queueName = QueueName("external-test-queue")
    val handler = handlers[queueName] as ExampleExternalQueueHandler

    jobEnqueuer.enqueue(
      queueName = queueName,
      body = "message_1",
      idempotencyKey = "idempotency_key_1",
      deliveryDelay = Duration.ofMillis(100),
      attributes = emptyMap(),
    )

    val latch = handler.counter
    latch.await()
    assertEquals(0, latch.count)

    val jobs = handler.jobs
    assertEquals(1, jobs.size)
  }

  @Test
  fun `batch enqueue with all three APIs works`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    // Reset counter for 5 jobs (2 + 1 + 2 from the three batch operations)
    handler.resetCounter(5)

    // Test batchEnqueue (suspend)
    val batchResult1 = jobEnqueuer.batchEnqueue(
      queueName = queueName,
      jobs = listOf(
        JobEnqueuer.JobRequest(
          body = "batch_message_1",
          idempotencyKey = "batch_key_1",
          deliveryDelay = Duration.ofMillis(100),
          attributes = mapOf("batch" to "1")
        ),
        JobEnqueuer.JobRequest(
          body = "batch_message_2",
          idempotencyKey = "batch_key_2",
          attributes = mapOf("batch" to "1")
        )
      )
    )

    assertTrue(batchResult1.isFullySuccessful)
    assertEquals(2, batchResult1.successfulIds.size)
    assertEquals(0, batchResult1.invalidIds.size)
    assertEquals(0, batchResult1.retriableIds.size)

    // Test batchEnqueueBlocking
    val batchResult2 = jobEnqueuer.batchEnqueueBlocking(
      queueName = queueName,
      jobs = listOf(
        JobEnqueuer.JobRequest(
          body = "batch_message_3",
          idempotencyKey = "batch_key_3",
          deliveryDelay = Duration.ZERO,
          attributes = mapOf("batch" to "2")
        )
      )
    )

    assertTrue(batchResult2.isFullySuccessful)
    assertEquals(1, batchResult2.successfulIds.size)

    // Test batchEnqueueAsync
    val batchResult3 = jobEnqueuer.batchEnqueueAsync(
      queueName = queueName,
      jobs = listOf(
        JobEnqueuer.JobRequest(
          body = "batch_message_4",
          idempotencyKey = "batch_key_4",
          attributes = mapOf("batch" to "3")
        ),
        JobEnqueuer.JobRequest(
          body = "batch_message_5",
          idempotencyKey = "batch_key_5",
          deliveryDelay = Duration.ofSeconds(1),
          attributes = mapOf("batch" to "3")
        )
      )
    ).join()

    assertTrue(batchResult3.isFullySuccessful)
    assertEquals(2, batchResult3.successfulIds.size)

    // Wait for all jobs to be processed
    val latch = handler.counter
    latch.await()
    assertEquals(0, latch.count)

    // Verify all 5 jobs were processed
    val jobs = handler.jobs
    assertEquals(5, jobs.size)
  }

  @Test
  fun `batch enqueue respects size limits`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    // Reset counter for 10 jobs (we'll test the max case)
    handler.resetCounter(10)

    // Test that batches > 10 jobs are rejected
    val tooManyJobs = (1..11).map { i ->
      JobEnqueuer.JobRequest(
        body = "message_$i",
        idempotencyKey = "key_$i"
      )
    }

    assertFailsWith<IllegalArgumentException> {
      jobEnqueuer.batchEnqueue(queueName, tooManyJobs)
    }

    assertFailsWith<IllegalArgumentException> {
      jobEnqueuer.batchEnqueueBlocking(queueName, tooManyJobs)
    }

    assertFailsWith<IllegalArgumentException> {
      jobEnqueuer.batchEnqueueAsync(queueName, tooManyJobs)
    }

    // Test that exactly 10 jobs works
    val maxJobs = (1..10).map { i ->
      JobEnqueuer.JobRequest(
        body = "message_$i",
        idempotencyKey = "max_key_$i"
      )
    }

    val result = jobEnqueuer.batchEnqueue(queueName, maxJobs)
    assertTrue(result.isFullySuccessful)
    assertEquals(10, result.successfulIds.size)
  }

  @Test
  fun `batch enqueue with mixed delivery delays and attributes`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    // Reset counter for 3 jobs
    handler.resetCounter(3)

    val mixedJobs = listOf(
      JobEnqueuer.JobRequest(
        body = "immediate_job",
        idempotencyKey = "immediate_key",
        deliveryDelay = Duration.ZERO,
        attributes = mapOf("priority" to "high", "type" to "immediate")
      ),
      JobEnqueuer.JobRequest(
        body = "delayed_job",
        idempotencyKey = "delayed_key",
        deliveryDelay = Duration.ofMillis(500),
        attributes = mapOf("priority" to "normal", "type" to "delayed")
      ),
      JobEnqueuer.JobRequest(
        body = "no_delay_job",
        idempotencyKey = "no_delay_key",
        attributes = mapOf("priority" to "low")
      )
    )

    val result = jobEnqueuer.batchEnqueue(queueName, mixedJobs)

    assertTrue(result.isFullySuccessful)
    assertEquals(3, result.successfulIds.size)
    assertEquals(setOf("immediate_key", "delayed_key", "no_delay_key"), result.successfulIds.toSet())

    // Wait for jobs to be processed
    val latch = handler.counter
    latch.await()
    assertEquals(0, latch.count)

    val jobs = handler.jobs
    assertEquals(3, jobs.size)
  }

  @Test
  fun `batch enqueue with too many attributes per job handles gracefully`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    // Reset counter for 1 job (only the valid job will be processed)
    handler.resetCounter(1)

    // Create a job with 10 attributes (exceeds limit of 9)
    val tooManyAttributes = (1..10).associate { "attr_$it" to "value_$it" }
    val validAttributes = (1..9).associate { "attr_$it" to "value_$it" }

    val jobs = listOf(
      JobEnqueuer.JobRequest(
        body = "invalid_message",
        idempotencyKey = "invalid_key",
        attributes = tooManyAttributes
      ),
      JobEnqueuer.JobRequest(
        body = "valid_message",
        idempotencyKey = "valid_key",
        attributes = validAttributes
      )
    )

    // Should not throw an exception, but handle invalid jobs gracefully
    val result = jobEnqueuer.batchEnqueue(queueName, jobs)

    // Should not be fully successful due to the invalid job
    assertEquals(false, result.isFullySuccessful)
    assertEquals(1, result.successfulIds.size)
    assertEquals(1, result.invalidIds.size)
    assertEquals(0, result.retriableIds.size)

    // Verify the correct job IDs are in the right categories
    assertEquals(listOf("valid_key"), result.successfulIds)
    assertEquals(listOf("invalid_key"), result.invalidIds)

    // Wait for the valid job to be processed
    val latch = handler.counter
    latch.await()
    assertEquals(0, latch.count)

    val jobs_processed = handler.jobs
    assertEquals(1, jobs_processed.size)
  }

  @Test
  fun `batch enqueue with all invalid jobs returns immediately`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    // No jobs should be processed
    handler.resetCounter(0)

    // Create jobs with too many attributes
    val tooManyAttributes = (1..10).associate { "attr_$it" to "value_$it" }

    val allInvalidJobs = listOf(
      JobEnqueuer.JobRequest(
        body = "invalid_message_1",
        idempotencyKey = "invalid_key_1",
        attributes = tooManyAttributes
      ),
      JobEnqueuer.JobRequest(
        body = "invalid_message_2",
        idempotencyKey = "invalid_key_2",
        attributes = tooManyAttributes
      )
    )

    val result = jobEnqueuer.batchEnqueue(queueName, allInvalidJobs)

    // Should not be successful, all jobs invalid
    assertEquals(false, result.isFullySuccessful)
    assertEquals(0, result.successfulIds.size)
    assertEquals(2, result.invalidIds.size)
    assertEquals(0, result.retriableIds.size)

    // Verify the invalid job IDs
    assertEquals(setOf("invalid_key_1", "invalid_key_2"), result.invalidIds.toSet())

    // No jobs should be processed
    assertEquals(0, handler.jobs.size)
  }
}

private class FakeQueueCreator(private val dockerSqs: DockerSqs) : ExternalDependency {
  private val queues = listOf("test-queue-1", "test-queue-1_retryq", "test-queue-1_dlq", "external-test-queue")

  override fun startup() {
  }

  override fun shutdown() {
  }

  override fun beforeEach() {
    queues.forEach {
      dockerSqs.client.createQueue(
        CreateQueueRequest.builder().queueName(it)
          .attributes(
            mapOf(
              QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
              QueueAttributeName.VISIBILITY_TIMEOUT to "20",
            )
          )
          .build()
      ).join()
    }
  }

  override fun afterEach() {
  }
}
