package misk.jobqueue

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Environment
import misk.hibernate.HibernateModule
import misk.hibernate.HibernateTestingModule
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.logging.getLogger
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.tokens.TokenGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.IllegalStateException
import kotlin.Throwable
import kotlin.error
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class FakeTransactionalJobQueueTest {
  @MiskTestModule private val module = TransactionalJobQueueTestModule()

  @Inject private lateinit var fakeTransactionalJobQueue: FakeTransactionalJobQueue
  @Inject private lateinit var unitEnqueuer: UnitEnqueuer
  @Inject private lateinit var logCollector: misk.logging.LogCollector
  @Inject @StarCraftDb private lateinit var transacter: Transacter

  @Test fun basic() {
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).isEmpty()

    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.HELLION, FACTORY_QUEUE)
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, STARPORT_QUEUE)
      unitEnqueuer.enqueue(session, Unit.LIBERATOR, STARPORT_QUEUE)
    }

    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).hasSize(1)
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).hasSize(2)

    fakeTransactionalJobQueue.handleJobs()

    assertThat(logCollector.takeMessages(Starport::class)).containsExactlyInAnyOrder(
      "received build MEDIVAC command",
      "received build LIBERATOR command"
    )

    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).isEmpty()
  }

  @Test fun handlesQueuesSeparately() {
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).isEmpty()

    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.HELLION, FACTORY_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).hasSize(1)

    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, STARPORT_QUEUE)
      unitEnqueuer.enqueue(session, Unit.LIBERATOR, STARPORT_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).hasSize(2)

    fakeTransactionalJobQueue.handleJobs(FACTORY_QUEUE)

    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).hasSize(2)

    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.HELLION, FACTORY_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).hasSize(1)

    fakeTransactionalJobQueue.handleJobs(STARPORT_QUEUE)

    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).hasSize(1)
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).isEmpty()
  }

  @Test fun assignsUniqueJobIds() {
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)).isEmpty()

    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.HELLION, FACTORY_QUEUE)
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, STARPORT_QUEUE)
      unitEnqueuer.enqueue(session, Unit.LIBERATOR, STARPORT_QUEUE)
    }

    val factoryJobs = fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)
    assertThat(factoryJobs).hasSize(1)

    val starportJobs = fakeTransactionalJobQueue.peekJobs(STARPORT_QUEUE)
    assertThat(starportJobs).hasSize(2)

    assertThat(factoryJobs[0].id).isEqualTo("fakej0bqee000000000000001")
    assertThat(starportJobs[0].id).isEqualTo("fakej0bqee000000000000002")
    assertThat(starportJobs[1].id).isEqualTo("fakej0bqee000000000000003")
  }

  @Test fun doesNotEnqueueInFailedTransaction() {
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()

    try {
      transacter.transaction { session ->
        unitEnqueuer.enqueue(session, Unit.HELLION, FACTORY_QUEUE)
        error("transaction failed")
      }
    } catch (expected: IllegalStateException) {
    }

    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()
  }

  @Test fun failedJobFailsTest() {
    assertThat(fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)).isEmpty()

    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, FACTORY_QUEUE) // This will throw in the job
    }

    val jobs = fakeTransactionalJobQueue.peekJobs(FACTORY_QUEUE)
    assertThat(jobs).hasSize(1)

    assertFailsWith<UnitException> {
      fakeTransactionalJobQueue.handleJobs()
    }
  }

  @Test fun retriesWork() {
    // The job will fail on its first run.
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).isEmpty()
    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, CRASHER_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).hasSize(1)
    assertFailsWith<UnitException> {
      fakeTransactionalJobQueue.handleJobs(CRASHER_QUEUE, assertAcknowledged = true, retries = 1)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).isEmpty()

    // If we retry at least once, it will complete the job the second time around.
    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, CRASHER_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).hasSize(1)
    fakeTransactionalJobQueue.handleJobs(CRASHER_QUEUE, assertAcknowledged = true, retries = 2)
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).isEmpty()
  }

  @Test fun exceptionsGoToDeadletter() {
    // The job will fail on its first run.
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).isEmpty()
    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, CRASHER_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).hasSize(1)
    assertThat(fakeTransactionalJobQueue.peekDeadlettered(CRASHER_QUEUE)).hasSize(0)
    assertFailsWith<UnitException> {
      fakeTransactionalJobQueue.handleJobs(CRASHER_QUEUE, assertAcknowledged = true, retries = 1)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekDeadlettered(CRASHER_QUEUE)).hasSize(1)

    // If we retry at least once, it will complete the job the second time around.
    fakeTransactionalJobQueue.reprocessDeadlettered(CRASHER_QUEUE, assertAcknowledged = true)
    assertThat(fakeTransactionalJobQueue.peekJobs(CRASHER_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekDeadlettered(CRASHER_QUEUE)).isEmpty()
  }

  @Test fun deadletterGoesToDeadletter() {
    assertThat(fakeTransactionalJobQueue.peekJobs(DEADLETTER_QUEUE)).isEmpty()
    transacter.transaction { session ->
      unitEnqueuer.enqueue(session, Unit.MEDIVAC, DEADLETTER_QUEUE)
    }
    assertThat(fakeTransactionalJobQueue.peekJobs(DEADLETTER_QUEUE)).hasSize(1)
    assertThat(fakeTransactionalJobQueue.peekDeadlettered(DEADLETTER_QUEUE)).hasSize(0)
    fakeTransactionalJobQueue.handleJobs(DEADLETTER_QUEUE, assertAcknowledged = false, retries = 1)

    assertThat(fakeTransactionalJobQueue.peekJobs(DEADLETTER_QUEUE)).isEmpty()
    assertThat(fakeTransactionalJobQueue.peekDeadlettered(DEADLETTER_QUEUE)).hasSize(1)
    assertThat(fakeTransactionalJobQueue.reprocessDeadlettered(DEADLETTER_QUEUE, false)).hasSize(1)

    // After reprocessing, all jobs returned to the deadletter queue again.
    assertThat(fakeTransactionalJobQueue.peekDeadlettered(DEADLETTER_QUEUE)).hasSize(1)
  }
}

private class TransactionalJobQueueTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DeploymentModule.forTesting())
    val config = MiskConfig.load<RootConfig>("starcraft", Environment.TESTING)
    install(HibernateTestingModule(StarCraftDb::class))
    install(HibernateModule(StarCraftDb::class, config.data_source))
    install(LogCollectorModule())
    install(FakeJobHandlerModule.create<Factory>(FACTORY_QUEUE))
    install(FakeJobHandlerModule.create<Starport>(STARPORT_QUEUE))
    install(FakeJobHandlerModule.create<Crasher>(CRASHER_QUEUE))
    install(FakeJobHandlerModule.create<Deadletter>(DEADLETTER_QUEUE))
    install(FakeJobQueueModule())
  }
}

private val FACTORY_QUEUE = QueueName("factory_queue")
private val STARPORT_QUEUE = QueueName("starport_queue")
private val CRASHER_QUEUE = QueueName("crasher_queue")
private val DEADLETTER_QUEUE = QueueName("deadletter_queue")

private enum class Unit {
  HELLION,
  MEDIVAC,
  LIBERATOR,
}

private class UnitException : Throwable()

private data class BuildUnitJob(val unit: Unit)

private class UnitEnqueuer @Inject private constructor(
  private val jobQueue: TransactionalJobQueue,
  moshi: Moshi
) {
  private val jobAdapter = moshi.adapter<BuildUnitJob>()

  fun enqueue(session: Session, unit: Unit, queue: QueueName) {
    val job = BuildUnitJob(unit)
    jobQueue.enqueue(session, queue, body = jobAdapter.toJson(job))
  }
}

private class Factory @Inject private constructor(moshi: Moshi) : JobHandler {
  private val jobAdapter = moshi.adapter<BuildUnitJob>()

  override fun handleJob(job: Job) {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received build ${deserializedJob.unit} command" }
    when (deserializedJob.unit) {
      Unit.HELLION -> job.acknowledge()
      else -> throw UnitException()
    }
  }

  companion object {
    private val log = getLogger<Factory>()
  }
}

private class Starport @Inject private constructor(moshi: Moshi) : JobHandler {
  private val jobAdapter = moshi.adapter<BuildUnitJob>()

  override fun handleJob(job: Job) {
    val deserializedJob = jobAdapter.fromJson(job.body)!!
    log.info { "received build ${deserializedJob.unit} command" }
    when (deserializedJob.unit) {
      Unit.MEDIVAC, Unit.LIBERATOR -> job.acknowledge()
      else -> throw UnitException()
    }
  }

  companion object {
    private val log = getLogger<Starport>()
  }
}

private class Crasher @Inject private constructor(
  val tokenGenerator: TokenGenerator
) : JobHandler {
  override fun handleJob(job: Job) {
    // Tokens are generated once per test run, starting from 1.
    if (tokenGenerator.generate().endsWith("1")) {
      throw UnitException()
    }
    job.acknowledge()
  }
}

private class Deadletter @Inject private constructor() : JobHandler {
  override fun handleJob(job: Job) {
    job.deadLetter()
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
private annotation class StarCraftDb

private data class RootConfig(val data_source: DataSourceConfig) : Config
