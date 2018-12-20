package misk.jobqueue.sqs

import com.google.common.util.concurrent.Service
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import java.time.Duration
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Install multiple job queues backed by AwsSQS.
 *
 * Usage
 *
 * In your misk service Module include:
 *  ```
 *  install(MiskJobModule(environment)
 *    .withHandler<SomeJobHandlerA>(JobQueueA::class, "job_queue_a")
 *    .withHandler<SomeJobHandlerB>(JobQueueB::class, "job_queue_b"))
 *
 *  ```
 *  and later access using:
 *  ```
 *  @Inject @JobQueueA lateinit var jobQueueA: MiskJobQueue`
 *  ```
 */

class MiskJobModule(private val environment: Environment) : KAbstractModule() {
  private lateinit var jobQueueProvider: Provider<JobQueue>
  private val jobQueuesMap =
      mutableListOf<JobQueueBinding>()

  inline fun <reified T : JobHandler> withHandler(
    qualifier: KClass<out Annotation>, queueName: String
  ) = withHandler(qualifier, queueName, T::class)

  fun withHandler(
    qualifier: KClass<out Annotation>,
    queueName: String,
    jobHandler: KClass<out JobHandler>
  ): MiskJobModule {
    jobQueuesMap += JobQueueBinding(qualifier,
        QueueName(queueName),
        jobHandler)
    return this
  }

  private fun configureJobQueues() {
    for ((qualifier, queueName, _) in jobQueuesMap) {
      val queueKey = MiskJobQueue::class.toKey(qualifier)

      bind(queueKey).toProvider(Provider<MiskJobQueue> {
        object : MiskJobQueue {
          override fun enqueue(
            body: String,
            deliveryDelay: Duration?,
            attributes: Map<String, String>
          ) {
            jobQueueProvider.get().enqueue(queueName, body, deliveryDelay, attributes)
          }
        }
      }).asSingleton()
    }
  }

  private fun bindHandlers(): Map<QueueName, Provider<JobHandler>> =
      jobQueuesMap.map { (qualifier, queueName, jobHandler) ->
        val jobHandlerKey = JobHandler::class.toKey(qualifier)
        val jobHandlerProvider = getProvider(jobHandlerKey)
        bind(jobHandlerKey).to(jobHandler.java)
        queueName to jobHandlerProvider
      }.toMap()

  override fun configure() {
    val jobQueueKey = JobQueue::class.toKey()
    jobQueueProvider = getProvider(jobQueueKey)
    val jobConsumerKey = JobConsumer::class.toKey()
    val jobConsumerProvider = getProvider(jobConsumerKey)
    val jobQueueServiceKey = MiskJobService::class.toKey()

    configureJobQueues()
    val boundHandlers = bindHandlers()

    bind(jobQueueServiceKey).toProvider(Provider<MiskJobService> {
      MiskJobService(jobConsumerProvider.get(), boundHandlers)
    }).asSingleton()

    multibind<Service>().to(jobQueueServiceKey)

    if (environment == Environment.DEVELOPMENT || environment == Environment.TESTING) {
      install(FakeAwsSqsModule())
    } else {
      install(AwsSqsJobQueueModule())
    }
  }

  data class JobQueueBinding(
    val qualifier: KClass<out Annotation>,
    val queueName: QueueName,
    val jobHandlerClass: KClass<out JobHandler>
  )
}

/**
 * Simple wrapper to avoid carrying [QueueName] everywhere.
 *
 * Assuming a jobqueue is bound to annotation @DinoQueue. Inject using the annotation:
 *
 *  `@Inject @DinoQueue lateinit var dinoQueue: MiskJobQueue`
 *
 */
interface MiskJobQueue {
  fun enqueue(
    body: String,
    deliveryDelay: Duration? = null,
    attributes: Map<String, String> = mapOf()
  )
}
