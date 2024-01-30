package misk.jobqueue

import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.jobqueue.testutilities.ColorException
import misk.jobqueue.testutilities.ENQUEUER_QUEUE
import misk.jobqueue.testutilities.EnqueuerJobHandler
import misk.jobqueue.testutilities.ExampleJob
import misk.jobqueue.testutilities.ExampleJobHint
import misk.jobqueue.testutilities.GREEN_QUEUE
import misk.jobqueue.testutilities.RED_QUEUE
import misk.logging.LogCollectorModule
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import wisp.logging.getLogger
import java.util.concurrent.ConcurrentHashMap

@MiskTest(startService = true)
class FakeJobQueueUnbatchedTest : FakeJobQueueTest() {
  @Suppress("unused")
  @MiskTestModule
  val module = TestModule()

  override val loggerClass = ExampleJobHandler::class
}

class TestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(LogCollectorModule())
    install(FakeJobHandlerModule.create<ExampleJobHandler>(RED_QUEUE))
    install(FakeJobHandlerModule.create<ExampleJobHandler>(GREEN_QUEUE))
    install(FakeJobHandlerModule.create<EnqueuerJobHandler>(ENQUEUER_QUEUE))
    install(FakeJobQueueModule())
  }
}

@Singleton
class ExampleJobHandler @Inject private constructor(moshi: Moshi) : JobHandler {
  private val jobAdapter = moshi.adapter<ExampleJob>()
  private val jobsExecutedOnce = ConcurrentHashMap<String, Boolean>()

  override fun handleJob(job: Job) {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received ${deserializedJob.color} job with message: ${deserializedJob.message}" }

    Assertions.assertThat(job.attributes).containsEntry("key", "value")

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
      else -> Unit
    }

    job.acknowledge()
  }

  companion object {
    private val log = getLogger<ExampleJobHandler>()
  }
}
