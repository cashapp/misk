package misk.jobqueue

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.logging.getLogger
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class FakeJobQueueTest {
  @MiskTestModule private val module = TestModule()

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
  fun assignsUniqueJobIds() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueRed("stop sign")
    exampleJobEnqueuer.enqueueGreen("dinosaur")
    exampleJobEnqueuer.enqueueGreen("android")

    val redJobs = fakeJobQueue.peekJobs(RED_QUEUE)
    assertThat(redJobs).hasSize(1)

    val greenJobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(greenJobs).hasSize(2)

    assertThat(redJobs[0].id).isEqualTo("fakej0bqee000000000000001")
    assertThat(greenJobs[0].id).isEqualTo("fakej0bqee000000000000002")
    assertThat(greenJobs[1].id).isEqualTo("fakej0bqee000000000000003")
  }

  @Test
  fun failedJobFailsTest() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("throw")

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    assertFailsWith<ColorException> {
      fakeJobQueue.handleJobs()
    }
  }

  @Test
  fun failsIfNotAcknowledged() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("dont-ack")

    val jobs = fakeJobQueue.peekJobs(GREEN_QUEUE)
    assertThat(jobs).hasSize(1)

    val e = assertFailsWith<IllegalStateException> {
      fakeJobQueue.handleJobs()
    }

    assertThat(e.message).isEqualTo("Expected ${jobs.first()} to be acknowledged after handling")
  }

  @Test
  fun expectedMissingAcknowledge() {
    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()

    exampleJobEnqueuer.enqueueGreen("dont-ack")

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
    assertThat(greenJob1.deliveryDelay).isEqualTo(oneSecondDeliveryDelay)
    assertThat(greenJob2.deliveryDelay).isNull()

    fakeJobQueue.handleJobs()

    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
        "received GREEN job with message: dinosaur",
        "received GREEN job with message: android",
        "received RED job with message: stop sign"
    )

    assertThat(fakeJobQueue.peekJobs(GREEN_QUEUE)).isEmpty()
    assertThat(fakeJobQueue.peekJobs(RED_QUEUE)).isEmpty()
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

private data class ExampleJob(val color: Color, val message: String)

private class ExampleJobEnqueuer @Inject private constructor(
  private val jobQueue: JobQueue,
  moshi: Moshi
) {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  fun enqueueRed(message: String, deliveryDelay: Duration? = null) {
    val job = ExampleJob(Color.RED, message)
    jobQueue.enqueue(RED_QUEUE, body = jobAdapter.toJson(job), deliveryDelay = deliveryDelay,
        attributes = mapOf("key" to "value"))
  }

  fun enqueueGreen(message: String, deliveryDelay: Duration? = null) {
    val job = ExampleJob(Color.GREEN, message)
    jobQueue.enqueue(GREEN_QUEUE, body = jobAdapter.toJson(job), deliveryDelay = deliveryDelay,
        attributes = mapOf("key" to "value"))
  }

  fun enqueueEnqueuer() {
    jobQueue.enqueue(ENQUEUER_QUEUE, body = "")
  }
}

private class ExampleJobHandler @Inject private constructor(moshi: Moshi) : JobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()

  override fun handleJob(job: Job) {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received ${deserializedJob.color} job with message: ${deserializedJob.message}" }

    assertThat(job.attributes).containsEntry("key", "value")

    when (deserializedJob.message) {
      "dont-ack" -> return
      "throw" -> throw ColorException()
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
