package misk.jobqueue

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import wisp.logging.getLogger
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class FakeJobQueueTest {
  @MiskTestModule private val module = TestModule()

  @Inject private lateinit var fakeClock: FakeClock
  @Inject private lateinit var fakeJobQueue: FakeJobQueue
  @Inject private lateinit var exampleJobEnqueuer: ExampleJobEnqueuer
  @Inject private lateinit var logCollector: LogCollector

  @Test
  fun basic() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueRed("stop sign")
    exampleJobEnqueuer.enqueueGreen("dinosaur")
    exampleJobEnqueuer.enqueueGreen("android")

    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).hasSize(1)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)

    fakeJobQueue.handleJobs()

    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: dinosaur",
      "received GREEN job with message: android",
      "received RED job with message: stop sign"
    )

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
  }

  @Test
  fun handlesQueuesSeparately() {
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
  fun assignsUniqueAndMonolithicallyIncrementedJobIds() {
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
  fun failedJobFailsTest() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("throw", hint = ExampleJobHint.THROW)

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    assertFailsWith<ColorException> {
      fakeJobQueue.handleJobs()
    }
  }

  @Test
  fun failsIfNotAcknowledged() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("dont-ack", hint = ExampleJobHint.DONT_ACK)

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    val e = assertFailsWith<IllegalStateException> {
      fakeJobQueue.handleJobs()
    }

    assertThat(e.message).isEqualTo("Expected ${jobs.first()} to be acknowledged after handling")
  }

  @Test
  fun deadletteredJobPassesIfNotAcknowledged() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("dead-letter", hint = ExampleJobHint.DEAD_LETTER)

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    val handledJobs = fakeJobQueue.handleJobs(assertAcknowledged = true)
    val onlyJob = handledJobs.single()
    assertThat(onlyJob.deadLettered).isTrue()
  }

  @Test
  fun expectedMissingAcknowledge() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("dont-ack", hint = ExampleJobHint.DONT_ACK)

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    val handledJobs = fakeJobQueue.handleJobs(assertAcknowledged = false)
    val onlyJob = handledJobs.single()
    assertThat(onlyJob.acknowledged).isFalse()
    assertThat(onlyJob.deadLettered).isFalse()
  }

  @Test
  fun allowsJobsToEnqueueOtherJobs() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueEnqueuer()
    fakeJobQueue.handleJobs()

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(1)
  }

  @Test
  fun includeDeliveryDelayInFakeJob() {
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

    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: dinosaur",
      "received GREEN job with message: android",
      "received RED job with message: stop sign"
    )

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
  }

  @Test
  fun jobsDoNotStartUntilDeliveryDelayElapses() {
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
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactly(
      "received GREEN job with message: J1:0s",
      "received GREEN job with message: J3:0s",
      "received GREEN job with message: J5:4s",
    )

    // Repeating processing does not change anything.
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
  }

  @Test
  fun jobsStartOnDeliveryDelayPassed() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1:0s")
    exampleJobEnqueuer.enqueueGreen("J2:0s+10s", Duration.ofSeconds(10))
    fakeClock.add(Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J3:5s+5s", Duration.ofSeconds(5))

    // Handle jobs 5s after test start: jobs with the delay stay in the queue.
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactly(
      "received GREEN job with message: J1:0s",
    )

    // Handle jobs 10s after test start.
    fakeClock.add(Duration.ofSeconds(5))
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactly(
      "received GREEN job with message: J2:0s+10s",
      "received GREEN job with message: J3:5s+5s",
    )
  }

  @Test
  fun jobsStartAfterDeliveryDelayPassed() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1:0s+10s", Duration.ofSeconds(10))
    fakeClock.add(Duration.ofSeconds(5))
    exampleJobEnqueuer.enqueueGreen("J2:5s+5s", Duration.ofSeconds(5))

    // Handle jobs 20s after test start.
    fakeClock.add(Duration.ofSeconds(15))
    fakeJobQueue.handleJobs(considerDelays = true)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactly(
      "received GREEN job with message: J1:0s+10s",
      "received GREEN job with message: J2:5s+5s",
    )
  }

  @Test
  fun jobsAreHandledOrderedByDeliveryDelaysInFakeJob() {
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
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactly(
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
  fun processIndividualJob() {
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

    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: J2",
    )

    // Process all jobs.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.handleJobs()).hasSize(2)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: J1",
      "received GREEN job with message: J3",
    )
  }

  @Test
  fun processIndividualDeadLetterJob() {
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
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: J1",
      "received GREEN job with message: J2",
      "received GREEN job with message: J3",
    )

    // Process Job 2.
    val job2 = fakeJobQueue.peekDeadlettered(GREEN_QUEUE)[1]
    assertThat(fakeJobQueue.handleJob(job2)).isFalse()
    assertThat(fakeJobQueue.reprocessDeadlettered(job2)).isTrue()
    assertThat(fakeJobQueue.reprocessDeadlettered(job2)).isFalse()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: J2",
    )

    // Process all jobs.
    assertThat(fakeJobQueue.peekDeadlettered(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.reprocessDeadlettered(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.peekDeadlettered(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: J1",
      "received GREEN job with message: J3",
    )
  }

  @Test
  fun unknownJobIsNotProcessed() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    // Setup jobs.
    exampleJobEnqueuer.enqueueGreen("J1")
    exampleJobEnqueuer.enqueueGreen("J2")

    // Process an unknown job.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    val unknownJob =
      FakeJob(GREEN_QUEUE, "unknown", "idempotenceKey", "body", mapOf(), fakeClock.instant())
    assertThat(fakeJobQueue.handleJob(unknownJob)).isFalse()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).isEmpty()

    // Process all jobs.
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).hasSize(2)
    assertThat(fakeJobQueue.handleJobs()).hasSize(2)
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: J1",
      "received GREEN job with message: J2",
    )
  }
}

private class TestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(LogCollectorModule())
    install(FakeJobHandlerModule.create<ExampleJobHandler>(RED_QUEUE))
    install(FakeJobHandlerModule.create<ExampleJobHandler>(GREEN_QUEUE))
    install(FakeJobHandlerModule.create<EnqueuerJobHandler>(ENQUEUER_QUEUE))
    install(FakeJobQueueModule())
  }
}

private val RED_QUEUE = QueueName("red_queue")
private val GREEN_QUEUE = QueueName("green_queue")
private val ENQUEUER_QUEUE = QueueName("first_step_queue")

private enum class Color {
  RED,
  GREEN
}

private class ColorException : Throwable()

private data class ExampleJob(
  val color: Color,
  val message: String,
  val hint: ExampleJobHint? = null
)

private enum class ExampleJobHint {
  DONT_ACK,
  THROW,
  THROW_ONCE,
  DEAD_LETTER,
  DEAD_LETTER_ONCE
}

private class ExampleJobEnqueuer @Inject private constructor(
  private val jobQueue: JobQueue,
  moshi: Moshi
) {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  fun enqueueRed(message: String, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    val job = ExampleJob(Color.RED, message, hint)
    jobQueue.enqueue(
      RED_QUEUE, body = jobAdapter.toJson(job), deliveryDelay = deliveryDelay,
      attributes = mapOf("key" to "value")
    )
  }

  fun enqueueGreen(message: String, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    val job = ExampleJob(Color.GREEN, message, hint)
    jobQueue.enqueue(
      GREEN_QUEUE, body = jobAdapter.toJson(job), deliveryDelay = deliveryDelay,
      attributes = mapOf("key" to "value")
    )
  }

  fun enqueueEnqueuer() {
    jobQueue.enqueue(ENQUEUER_QUEUE, body = "")
  }
}

@Singleton
private class ExampleJobHandler @Inject private constructor(moshi: Moshi) : JobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()
  private val jobsExecutedOnce = ConcurrentHashMap<String, Boolean>()

  override fun handleJob(job: Job) {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received ${deserializedJob.color} job with message: ${deserializedJob.message}" }

    assertThat(job.attributes).containsEntry("key", "value")

    val key = "${deserializedJob.color}:${deserializedJob.hint}:${deserializedJob.message}"
    val jobExecutedBefore = jobsExecutedOnce.putIfAbsent(key, true) == true
    when (deserializedJob.hint) {
      ExampleJobHint.DONT_ACK -> return
      ExampleJobHint.DEAD_LETTER -> {
        job.deadLetter()
        return
      }
      ExampleJobHint.DEAD_LETTER_ONCE -> if (!jobExecutedBefore) {
        job.deadLetter()
        return
      }
      ExampleJobHint.THROW -> throw ColorException()
      ExampleJobHint.THROW_ONCE -> if (!jobExecutedBefore) {
        throw ColorException()
      }
    }

    job.acknowledge()
  }

  companion object {
    private val log = getLogger<ExampleJobHandler>()
  }
}

private class EnqueuerJobHandler @Inject private constructor(
  private val jobQueue: JobQueue,
  moshi: Moshi
) : JobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  override fun handleJob(job: Job) {
    jobQueue.enqueue(
      queueName = GREEN_QUEUE,
      body = jobAdapter.toJson(ExampleJob(color = Color.GREEN, message = "We made it!"))
    )
    job.acknowledge()
  }
}
