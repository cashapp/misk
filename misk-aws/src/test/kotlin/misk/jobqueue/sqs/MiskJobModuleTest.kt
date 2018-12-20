package misk.jobqueue.sqs

import com.google.inject.util.Modules
import misk.MiskServiceModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.jobqueue.Job
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.tokens.FakeTokenGeneratorModule
import misk.tokens.TokenGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.test.assertTrue

@MiskTest(startService = true)
class MiskJobModuleTest {

  @MiskTestModule
  val module = Modules.combine(TestJobQueueModule())

  @Inject @JobQueueA lateinit var jobQueueA: MiskJobQueue
  @Inject @JobQueueB lateinit var jobQueueB: MiskJobQueue
  @Inject lateinit var fakeSqs: FakeSqs

  @Test
  fun allJobHandlersInstalled() {
    val expectedHandlers: Map<QueueName, KClass<*>> = mapOf(
        QueueName("dino_job_queue_a") to FakeJobHandlerA::class,
        QueueName("dino_job_queue_b") to FakeJobHandlerB::class
    )

    with(fakeSqs.handlers) {
      assertThat(size).isEqualTo(expectedHandlers.size)
      forEach {
        assertThat(this).containsKey(it.key)
        assertTrue { expectedHandlers[it.key]!!.isInstance(it.value) }
      }
    }
  }

  @Test
  fun jobEnqueueToCorrectQueue() {
    val queueNameA = QueueName("dino_job_queue_a")
    val queueNameB = QueueName("dino_job_queue_b")

    fakeSqs.checkNextEnqueue(queueNameA, "job_a")
    jobQueueA.enqueue("job_a")

    fakeSqs.checkNextEnqueue(queueNameB, "job_b")
    jobQueueB.enqueue("job_b")
  }

  class TestJobQueueModule : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())
      install(MiskJobModule(Environment.TESTING)
          .withHandler<FakeJobHandlerA>(
              JobQueueA::class, "dino_job_queue_a")
          .withHandler<FakeJobHandlerB>(
              JobQueueB::class, "dino_job_queue_b"))
    }
  }

  class FakeJobHandlerA : JobHandler {
    override fun handleJob(job: Job) {}
  }

  class FakeJobHandlerB : JobHandler {
    override fun handleJob(job: Job) {}
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  annotation class JobQueueA

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  annotation class JobQueueB
}
