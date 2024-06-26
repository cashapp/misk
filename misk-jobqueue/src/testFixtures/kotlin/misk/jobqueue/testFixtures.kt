package misk.jobqueue

import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.moshi.adapter
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class ExampleJobQueuesTestingModule : KAbstractModule() {
  override fun configure() {
    install(FakeJobHandlerModule.create<ExampleJobHandler>(RED_QUEUE))
    install(FakeJobHandlerModule.create<ExampleJobHandler>(GREEN_QUEUE))
    install(FakeJobHandlerModule.create<EnqueuerJobHandler>(ENQUEUER_QUEUE))
    install(FakeJobQueueModule())
  }
}

val RED_QUEUE = QueueName("red_queue")
val GREEN_QUEUE = QueueName("green_queue")
internal val ENQUEUER_QUEUE = QueueName("first_step_queue")

internal enum class Color {
  RED,
  GREEN
}

class ColorException : Exception()

internal data class ExampleJob(
  val color: Color,
  val message: String,
  val hint: ExampleJobHint? = null
)

enum class ExampleJobHint {
  DONT_ACK,
  THROW,
  THROW_ONCE,
  DEAD_LETTER,
  DEAD_LETTER_ONCE,
  DELAY_ONCE,
}

class ExampleJobEnqueuer @Inject private constructor(
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

  fun batchEnqueueRed(messages: List<String>, deliveryDelay: Duration? = null, hint: ExampleJobHint? = null) {
    jobQueue.batchEnqueue(RED_QUEUE, messages.map {
      JobQueue.JobRequest(
        body = jobAdapter.toJson(ExampleJob(Color.RED, it, hint)),
        deliveryDelay = deliveryDelay,
        attributes = mapOf("key" to "value")
      )
    })
  }

  fun enqueueEnqueuer() {
    jobQueue.enqueue(ENQUEUER_QUEUE, body = "")
  }
}

@Singleton
class ExampleJobHandler @Inject private constructor(moshi: Moshi) : JobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()
  private val jobsExecutedOnce = ConcurrentHashMap<String, Boolean>()

  override fun handleJob(job: Job) {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received ${deserializedJob.color} job with message: ${deserializedJob.message}" }

    check(job.attributes["key"] == "value") {
      "Expected attribute key to be 'value' but was ${job.attributes["key"]} instead"
    }

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
      ExampleJobHint.DELAY_ONCE -> if (!jobExecutedBefore) {
        job.delayWithBackoff()
        throw ColorException()
      }
      else -> Unit
    }

    job.acknowledge()
  }

  companion object {
    private val log = getLogger<ExampleJobHandler>()
  }
}

internal class EnqueuerJobHandler @Inject private constructor(
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
