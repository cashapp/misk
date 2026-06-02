package misk.jobqueue.v2

import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.logging.getLogger
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class FakeJobEnqueuerTest {
  @MiskTestModule private val module = TestModule()

  @Inject private lateinit var exampleJobEnqueuer: ExampleJobEnqueuer
  @Inject private lateinit var fakeClock: FakeClock
  @Inject private lateinit var fakeJobQueue: FakeJobEnqueuer
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var moshi: Moshi

  @Test
  fun basic() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueRed("stop sign")
    exampleJobEnqueuer.enqueueGreen("dinosaur")
    exampleJobEnqueuer.enqueueGreen("android")

    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).hasSize(1)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)

    fakeJobQueue.handleJobs()

    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder(
        "received GREEN job with message: dinosaur",
        "received GREEN job with message: android",
        "received RED job with message: stop sign",
      )

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
  }

  @Test
  fun handlesQueuesSeparately() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueRed("stop sign")

    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).hasSize(1)

    exampleJobEnqueuer.enqueueGreen("dinosaur")
    exampleJobEnqueuer.enqueueGreen("android")

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)

    fakeJobQueue.handleJobs(GREEN_QUEUE)

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).hasSize(1)

    exampleJobEnqueuer.enqueueGreen("pickle")
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(1)

    fakeJobQueue.handleJobs(RED_QUEUE)

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(1)
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
  }

  @Test
  fun genericFailureQueueThrowsOnEnqueue() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    // Fails the next 2 enqueue
    fakeJobQueue.pushFailure(Exception(":) 1"))
    fakeJobQueue.pushFailure(Exception(":) 2"))
    val e1 = assertFailsWith<Exception> { exampleJobEnqueuer.enqueueRed("dropped") }
    val e2 = assertFailsWith<Exception> { exampleJobEnqueuer.enqueueGreen("dropped") }
    assertThat(e1.message).isEqualTo(":) 1")
    assertThat(e2.message).isEqualTo(":) 2")

    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueRed("hello")

    val redJobs = fakeJobQueue.handleJobs(RED_QUEUE)
    assertThat(redJobs).hasSize(1)

    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder("received RED job with message: hello")
  }

  @Test
  fun queueSpecificFailureQueueThrowsOnEnqueue() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    // Fails the next 2 enqueue
    fakeJobQueue.pushFailure(Exception(":) 1"), RED_QUEUE)
    fakeJobQueue.pushFailure(Exception(":) 2"), RED_QUEUE)
    exampleJobEnqueuer.enqueueGreen("not dropped green")
    val e1 = assertFailsWith<Exception> { exampleJobEnqueuer.enqueueRed("dropped") }
    val e2 = assertFailsWith<Exception> { exampleJobEnqueuer.enqueueRed("dropped") }
    assertThat(e1.message).isEqualTo(":) 1")
    assertThat(e2.message).isEqualTo(":) 2")

    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(1)

    exampleJobEnqueuer.enqueueRed("not dropped red")

    val redJobs = fakeJobQueue.handleJobs(RED_QUEUE)
    assertThat(redJobs).hasSize(1)
    val greenJobs = fakeJobQueue.handleJobs(GREEN_QUEUE)
    assertThat(greenJobs).hasSize(1)

    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder(
        "received RED job with message: not dropped red",
        "received GREEN job with message: not dropped green",
      )
  }

  @Test
  fun assignsUniqueAndMonolithicallyIncrementedJobIds() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    // 10 jobs to test 0-padding in ids.
    exampleJobEnqueuer.enqueueRed("stop sign")
    exampleJobEnqueuer.enqueueGreen("dinosaur")
    exampleJobEnqueuer.enqueueGreen("android")
    exampleJobEnqueuer.enqueueRed("more red")
    exampleJobEnqueuer.enqueueRed("more red")
    exampleJobEnqueuer.enqueueRed("more red")
    exampleJobEnqueuer.enqueueRed("more red")
    exampleJobEnqueuer.enqueueRed("more red")
    exampleJobEnqueuer.enqueueRed("more red")
    exampleJobEnqueuer.enqueueRed("more red")

    val redJobs = fakeJobQueue.peekJobs(RED_QUEUE)
    assertThat(redJobs).hasSize(8)

    val greenJobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(greenJobs).hasSize(2)

    assertThat(redJobs[0].id).isEqualTo("fakej0bqee000000000000001")
    assertThat(greenJobs[0].id).isEqualTo("fakej0bqee000000000000002")
    assertThat(greenJobs[1].id).isEqualTo("fakej0bqee000000000000003")
    assertThat(redJobs[1].id).isEqualTo("fakej0bqee000000000000004")
    assertThat(redJobs[2].id).isEqualTo("fakej0bqee000000000000005")
    assertThat(redJobs[3].id).isEqualTo("fakej0bqee000000000000006")
    assertThat(redJobs[4].id).isEqualTo("fakej0bqee000000000000007")
    assertThat(redJobs[5].id).isEqualTo("fakej0bqee000000000000008")
    assertThat(redJobs[6].id).isEqualTo("fakej0bqee000000000000009")
    assertThat(redJobs[7].id).isEqualTo("fakej0bqee000000000000010")
  }

  @Test
  fun failedJobFailsTest() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("throw", hint = ExampleJobHint.THROW)

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    assertFailsWith<ColorException> { fakeJobQueue.handleJobs() }
  }

  @Test
  fun deadletteredJobPassesIfNotAcknowledged() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("dead-letter", hint = ExampleJobHint.DEAD_LETTER)

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    val handledJobs = fakeJobQueue.handleJobs(assertAcknowledged = true)
    val onlyJob = handledJobs.single()
    assertThat(onlyJob.deadLettered).isTrue()
  }

  @Test
  fun allowsJobsToEnqueueOtherJobs() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueEnqueuer()
    fakeJobQueue.handleJobs()

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(1)
  }

  @Test
  fun includeDeliveryDelayInFakeJob() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    val now = fakeClock.instant()
    val oneSecondDeliveryDelay = Duration.of(1000L, ChronoUnit.MILLIS)
    exampleJobEnqueuer.enqueueRed("stop sign", oneSecondDeliveryDelay)
    exampleJobEnqueuer.enqueueGreen("dinosaur", oneSecondDeliveryDelay)
    exampleJobEnqueuer.enqueueGreen("android")

    val redJobs = fakeJobQueue.peekJobs(RED_QUEUE)
    assertThat(redJobs).hasSize(1)

    val redJob = redJobs.first() as FakeJob
    assertThat(redJob.deliveryDelay).isEqualTo(oneSecondDeliveryDelay)

    val greenJobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(greenJobs).hasSize(2)
    val greenJob1 = greenJobs[0] as FakeJob
    val greenJob2 = greenJobs[1] as FakeJob
    assertThat(greenJob1.enqueuedAt).isEqualTo(now)
    assertThat(greenJob1.deliveryDelay).isEqualTo(oneSecondDeliveryDelay)
    assertThat(greenJob1.deliverAt).isEqualTo(now.plus(oneSecondDeliveryDelay))
    assertThat(greenJob2.enqueuedAt).isEqualTo(now)
    assertThat(greenJob2.deliveryDelay).isNull()
    assertThat(greenJob2.deliverAt).isEqualTo(now)

    fakeJobQueue.handleJobs()

    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder(
        "received GREEN job with message: dinosaur",
        "received GREEN job with message: android",
        "received RED job with message: stop sign",
      )

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
  }

  @Test
  fun jobsDoNotStartUntilDeliveryDelayElapses() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1:0s")
    exampleJobEnqueuer.enqueueGreen("J2:0s+10s", Duration.ofSeconds(10))
    exampleJobEnqueuer.enqueueGreen("J3:0s")
    fakeClock.add(Duration.ofSeconds(4))
    exampleJobEnqueuer.enqueueGreen("J4:4s+5s", Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J5:4s")

    // Handle jobs 4s after test start.
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactly(
        "received GREEN job with message: J1:0s",
        "received GREEN job with message: J3:0s",
        "received GREEN job with message: J5:4s",
      )

    // Repeating processing does not change anything.
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
  }

  @Test
  fun jobsStartOnDeliveryDelayPassed() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1:0s")
    exampleJobEnqueuer.enqueueGreen("J2:0s+10s", Duration.ofSeconds(10))
    fakeClock.add(Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J3:5s+5s", Duration.ofSeconds(5))

    // Handle jobs 5s after test start: jobs with the delay stay in the queue.
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactly("received GREEN job with message: J1:0s")

    // Handle jobs 10s after test start.
    fakeClock.add(Duration.ofSeconds(5))
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactly("received GREEN job with message: J2:0s+10s", "received GREEN job with message: J3:5s+5s")
  }

  @Test
  fun jobsStartAfterDeliveryDelayPassed() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1:0s+10s", Duration.ofSeconds(10))
    fakeClock.add(Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J2:5s+5s", Duration.ofSeconds(5))

    // Handle jobs 20s after test start.
    fakeClock.add(Duration.ofSeconds(15))
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactly("received GREEN job with message: J1:0s+10s", "received GREEN job with message: J2:5s+5s")
  }

  @Test
  fun jobsAreHandledOrderedByDeliveryDelaysInFakeJob() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1:0s")
    exampleJobEnqueuer.enqueueGreen("J2:0s+10s", Duration.ofSeconds(10))
    exampleJobEnqueuer.enqueueGreen("J3:0s")
    exampleJobEnqueuer.enqueueGreen("J4:0s+5s", Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J5:0s")

    fakeClock.add(Duration.ofSeconds(4))
    exampleJobEnqueuer.enqueueGreen("J6:4s+1s", Duration.ofSeconds(1))
    exampleJobEnqueuer.enqueueGreen("J7:4s+5s", Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J8:4s")

    // Handle everything 10s after test start.
    fakeClock.add(Duration.ofSeconds(6))
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Check order.
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactly(
        "received GREEN job with message: J1:0s",
        "received GREEN job with message: J3:0s",
        "received GREEN job with message: J5:0s",
        "received GREEN job with message: J8:4s",
        "received GREEN job with message: J4:0s+5s",
        "received GREEN job with message: J6:4s+1s",
        "received GREEN job with message: J7:4s+5s",
        "received GREEN job with message: J2:0s+10s",
      )
  }

  @Test
  fun processIndividualJob() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1")
    exampleJobEnqueuer.enqueueGreen("J2")
    exampleJobEnqueuer.enqueueGreen("J3")

    // Process Job 2.
    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(3)
    val job2 = jobs[1]
    assertThat(fakeJobQueue.handleJob(job2)).isTrue()
    // Same job is not processed the second time.
    assertThat(fakeJobQueue.handleJob(job2)).isFalse()

    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder("received GREEN job with message: J2")

    // Process all jobs.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.handleJobs()).hasSize(2)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder("received GREEN job with message: J1", "received GREEN job with message: J3")
  }

  @Test
  fun processIndividualDeadLetterJob() = runTest {
    // Setup dead letter queue.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    exampleJobEnqueuer.enqueueGreen("J1", hint = ExampleJobHint.DEAD_LETTER_ONCE)
    exampleJobEnqueuer.enqueueGreen("J2", hint = ExampleJobHint.DEAD_LETTER_ONCE)
    exampleJobEnqueuer.enqueueGreen("J3", hint = ExampleJobHint.DEAD_LETTER_ONCE)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(3)
    assertThat(fakeJobQueue.peekDeadlettered(GREEN_QUEUE)).isEmpty()
    fakeJobQueue.handleJobs(GREEN_QUEUE, assertAcknowledged = false, retries = 1)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekDeadlettered(GREEN_QUEUE)).hasSize(3)
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder(
        "received GREEN job with message: J1",
        "received GREEN job with message: J2",
        "received GREEN job with message: J3",
      )

    // Process Job 2.
    val job2 = fakeJobQueue.peekDeadlettered(GREEN_QUEUE)[1]
    assertThat(fakeJobQueue.handleJob(job2)).isFalse()
    assertThat(fakeJobQueue.reprocessDeadlettered(job2)).isTrue()
    assertThat(fakeJobQueue.reprocessDeadlettered(job2)).isFalse()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder("received GREEN job with message: J2")

    // Process all jobs.
    assertThat(fakeJobQueue.peekDeadlettered(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.reprocessDeadlettered(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.peekDeadlettered(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder("received GREEN job with message: J1", "received GREEN job with message: J3")
  }

  @Test
  fun unknownJobIsNotProcessed() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1")
    exampleJobEnqueuer.enqueueGreen("J2")

    // Process an unknown job.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    val unknownJob = FakeJob(GREEN_QUEUE, "unknown", "idempotenceKey", "body", mapOf(), fakeClock.instant())
    assertThat(fakeJobQueue.handleJob(unknownJob)).isFalse()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).isEmpty()

    // Process all jobs.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.handleJobs()).hasSize(2)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder("received GREEN job with message: J1", "received GREEN job with message: J2")
  }

  @Test
  fun batchEnqueueAllThreeApisWork() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Create properly formatted JSON job bodies using injected Moshi (with Kotlin support)
    val jobAdapter = moshi.adapter<ExampleJob>()

    // Test batchEnqueue (suspend)
    val result1 =
      fakeJobQueue.batchEnqueue(
        GREEN_QUEUE,
        listOf(
          JobEnqueuer.JobRequest(
            body = jobAdapter.toJson(ExampleJob(Color.GREEN, "batch_msg_1")),
            idempotencyKey = "batch_key_1",
            deliveryDelay = Duration.ofMillis(100),
            attributes = mapOf("key" to "value"),
          ),
          JobEnqueuer.JobRequest(
            body = jobAdapter.toJson(ExampleJob(Color.GREEN, "batch_msg_2")),
            idempotencyKey = "batch_key_2",
            attributes = mapOf("key" to "value"),
          ),
        ),
      )
    assertThat(result1.isFullySuccessful).isTrue()
    assertThat(result1.successfulIds).containsExactly("batch_key_1", "batch_key_2")
    assertThat(result1.invalidIds).isEmpty()
    assertThat(result1.retriableIds).isEmpty()

    // Test batchEnqueueBlocking
    val result2 =
      fakeJobQueue.batchEnqueueBlocking(
        RED_QUEUE,
        listOf(
          JobEnqueuer.JobRequest(
            body = jobAdapter.toJson(ExampleJob(Color.RED, "batch_red_msg")),
            idempotencyKey = "batch_red_key",
            attributes = mapOf("key" to "value"),
          )
        ),
      )
    assertThat(result2.isFullySuccessful).isTrue()
    assertThat(result2.successfulIds).containsExactly("batch_red_key")

    // Test batchEnqueueAsync
    val result3 =
      fakeJobQueue
        .batchEnqueueAsync(
          GREEN_QUEUE,
          listOf(
            JobEnqueuer.JobRequest(
              body = jobAdapter.toJson(ExampleJob(Color.GREEN, "batch_async_1")),
              idempotencyKey = "batch_async_key_1",
              attributes = mapOf("key" to "value"),
            ),
            JobEnqueuer.JobRequest(
              body = jobAdapter.toJson(ExampleJob(Color.GREEN, "batch_async_2")),
              idempotencyKey = "batch_async_key_2",
              deliveryDelay = Duration.ofSeconds(1),
              attributes = mapOf("key" to "value"),
            ),
          ),
        )
        .join()
    assertThat(result3.isFullySuccessful).isTrue()
    assertThat(result3.successfulIds).containsExactly("batch_async_key_1", "batch_async_key_2")

    // Verify jobs are in queues
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(4)
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).hasSize(1)

    // Process all jobs
    fakeJobQueue.handleJobs()

    assertThat(logCollector.takeMessages(ExampleJobHandler::class))
      .containsExactlyInAnyOrder(
        "received GREEN job with message: batch_msg_1",
        "received GREEN job with message: batch_msg_2",
        "received GREEN job with message: batch_async_1",
        "received GREEN job with message: batch_async_2",
        "received RED job with message: batch_red_msg",
      )
  }

  @Test
  fun batchEnqueueRespectsSizeLimit() = runTest {
    // Test that batches > 10 jobs are rejected
    val tooManyJobs = (1..11).map { i -> JobEnqueuer.JobRequest("message_$i", "key_$i") }

    assertFailsWith<IllegalArgumentException> { fakeJobQueue.batchEnqueue(GREEN_QUEUE, tooManyJobs) }

    assertFailsWith<IllegalArgumentException> { fakeJobQueue.batchEnqueueBlocking(GREEN_QUEUE, tooManyJobs) }

    assertFailsWith<IllegalArgumentException> { fakeJobQueue.batchEnqueueAsync(GREEN_QUEUE, tooManyJobs) }

    // Test that exactly 10 jobs works
    val maxJobs = (1..10).map { i -> JobEnqueuer.JobRequest("message_$i", "max_key_$i") }

    val result = fakeJobQueue.batchEnqueue(GREEN_QUEUE, maxJobs)
    assertThat(result.isFullySuccessful).isTrue()
    assertThat(result.successfulIds).hasSize(10)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(10)
  }

  @Test
  fun batchEnqueueWithFailuresReturnsRetriableJobs() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Set up a failure for GREEN_QUEUE
    fakeJobQueue.pushFailure(RuntimeException("SQS is down"), GREEN_QUEUE)

    val jobs = listOf(JobEnqueuer.JobRequest("job1", "key1"), JobEnqueuer.JobRequest("job2", "key2"))

    val result = fakeJobQueue.batchEnqueueAsync(GREEN_QUEUE, jobs).join()

    // When there's a failure, all jobs should be marked as retriable
    assertThat(result.isFullySuccessful).isFalse()
    assertThat(result.successfulIds).isEmpty()
    assertThat(result.invalidIds).isEmpty()
    assertThat(result.retriableIds).containsExactly("key1", "key2")

    // No jobs should actually be enqueued
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
  }

  @Test
  fun batchEnqueueWithMixedDeliveryDelaysAndAttributes() = runTest {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    val mixedJobs =
      listOf(
        JobEnqueuer.JobRequest(
          body = "immediate_job",
          idempotencyKey = "immediate_key",
          deliveryDelay = Duration.ZERO,
          attributes = mapOf("priority" to "high"),
        ),
        JobEnqueuer.JobRequest(
          body = "delayed_job",
          idempotencyKey = "delayed_key",
          deliveryDelay = Duration.ofMillis(500),
          attributes = mapOf("priority" to "normal", "type" to "delayed"),
        ),
        JobEnqueuer.JobRequest(
          body = "no_delay_job",
          idempotencyKey = "no_delay_key",
          attributes = mapOf("priority" to "low"),
        ),
      )

    val result = fakeJobQueue.batchEnqueue(GREEN_QUEUE, mixedJobs)

    assertThat(result.isFullySuccessful).isTrue()
    assertThat(result.successfulIds).containsExactly("immediate_key", "delayed_key", "no_delay_key")

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(3)

    // Verify job attributes and delays are preserved
    val fakeJobs = jobs.map { it as FakeJob }

    val immediateJob = fakeJobs.find { it.idempotenceKey == "immediate_key" }!!
    assertThat(immediateJob.body).isEqualTo("immediate_job")
    assertThat(immediateJob.deliveryDelay).isEqualTo(Duration.ZERO)
    assertThat(immediateJob.attributes).containsEntry("priority", "high")

    val delayedJob = fakeJobs.find { it.idempotenceKey == "delayed_key" }!!
    assertThat(delayedJob.body).isEqualTo("delayed_job")
    assertThat(delayedJob.deliveryDelay).isEqualTo(Duration.ofMillis(500))
    assertThat(delayedJob.attributes).containsEntry("priority", "normal")
    assertThat(delayedJob.attributes).containsEntry("type", "delayed")

    val noDelayJob = fakeJobs.find { it.idempotenceKey == "no_delay_key" }!!
    assertThat(noDelayJob.body).isEqualTo("no_delay_job")
    assertThat(noDelayJob.deliveryDelay).isNull()
    assertThat(noDelayJob.attributes).containsEntry("priority", "low")
  }
}

private class TestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(LogCollectorModule())
    install(FakeJobHandlerModule.create<ExampleJobHandler>(RED_QUEUE))
    install(FakeJobHandlerModule.create<ExampleJobHandler>(GREEN_QUEUE))
    install(FakeJobHandlerModule.create<EnqueuerJobHandler>(ENQUEUER_QUEUE))
    install(FakeJobEnqueuerModule())
  }
}

internal val RED_QUEUE = QueueName("red_queue")
internal val GREEN_QUEUE = QueueName("green_queue")
internal val ENQUEUER_QUEUE = QueueName("first_step_queue")

internal enum class Color {
  RED,
  GREEN,
}

internal class ColorException : Exception()

internal data class ExampleJob(val color: Color, val message: String, val hint: ExampleJobHint? = null)

internal enum class ExampleJobHint {
  DONT_ACK,
  THROW,
  THROW_ONCE,
  DEAD_LETTER,
  DEAD_LETTER_ONCE,
  //  DELAY_ONCE,
}

internal fun Job.handle(hint: ExampleJobHint?, hasExecutedBefore: Boolean): JobStatus =
  when (hint) {
    ExampleJobHint.DONT_ACK -> throw ColorException()
    ExampleJobHint.DEAD_LETTER -> JobStatus.DEAD_LETTER
    ExampleJobHint.DEAD_LETTER_ONCE ->
      if (!hasExecutedBefore) {
        JobStatus.DEAD_LETTER
      } else {
        JobStatus.OK
      }
    ExampleJobHint.THROW -> throw ColorException()
    ExampleJobHint.THROW_ONCE ->
      if (!hasExecutedBefore) {
        throw ColorException()
      } else {
        JobStatus.OK
      }

    null -> JobStatus.OK
  }

internal class ExampleJobEnqueuer @Inject private constructor(private val jobQueue: JobEnqueuer, moshi: Moshi) {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  suspend fun enqueueRed(message: String, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    val job = ExampleJob(Color.RED, message, hint)
    jobQueue.enqueue(
      RED_QUEUE,
      body = jobAdapter.toJson(job),
      deliveryDelay = deliveryDelay,
      attributes = mapOf("key" to "value"),
    )
  }

  suspend fun enqueueGreen(message: String, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    val job = ExampleJob(Color.GREEN, message, hint)
    jobQueue.enqueue(
      GREEN_QUEUE,
      body = jobAdapter.toJson(job),
      deliveryDelay = deliveryDelay,
      attributes = mapOf("key" to "value"),
    )
  }

  suspend fun enqueueEnqueuer() {
    jobQueue.enqueue(ENQUEUER_QUEUE, body = "")
  }
}

@Singleton
internal class ExampleJobHandler @Inject private constructor(moshi: Moshi) : SuspendingJobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()
  private val jobsExecutedOnce = ConcurrentHashMap<String, Boolean>()

  override suspend fun handleJob(job: Job): JobStatus {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received ${deserializedJob.color} job with message: ${deserializedJob.message}" }

    assertThat(job.attributes).containsEntry("key", "value")

    val key = "${deserializedJob.color}:${deserializedJob.hint}:${deserializedJob.message}"
    val jobExecutedBefore = jobsExecutedOnce.putIfAbsent(key, true) == true
    return job.handle(deserializedJob.hint, jobExecutedBefore)
  }

  companion object {
    private val log = getLogger<ExampleJobHandler>()
  }
}

internal class EnqueuerJobHandler @Inject private constructor(private val jobEnqueuer: JobEnqueuer, moshi: Moshi) :
  SuspendingJobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  override suspend fun handleJob(job: Job): JobStatus {
    jobEnqueuer.enqueue(
      queueName = GREEN_QUEUE,
      body = jobAdapter.toJson(ExampleJob(color = Color.GREEN, message = "We made it!")),
    )
    return JobStatus.OK
  }
}
