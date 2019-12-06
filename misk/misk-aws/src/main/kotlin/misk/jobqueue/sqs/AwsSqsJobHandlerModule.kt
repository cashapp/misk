package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.jobqueue.JobHandler
import misk.jobqueue.QueuesKey
import misk.jobqueue.QueueName
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for one or more SQS queues.
 *
 * @param queueNames a list of names for SQS queues. If more than one queue name is provided, these
 * will be treated as a list of prioritized queues (in priority order, first queue is top priority).
 *
 * See [misk.jobqueue.JobConsumer] for details
 */
class AwsSqsJobHandlerModule<T : JobHandler> private constructor(
  private val queueNames: List<QueueName>,
  private val handler: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    newMapBinder(QueuesKey::class, JobHandler::class).addBinding(QueuesKey(queueNames)).to(handler.java)
    install(ServiceModule<AwsSqsJobHandlerSubscriptionService>())
  }

  companion object {
    inline fun <reified T : JobHandler> create(queueName: QueueName):
        AwsSqsJobHandlerModule<T> = create(listOf(queueName), T::class)

    inline fun <reified T : JobHandler> createPrioritized(vararg queueNames: QueueName):
        AwsSqsJobHandlerModule<T> = create(queueNames.asList(), T::class)

    @JvmStatic
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>
    ): AwsSqsJobHandlerModule<T> {
      return create(listOf(queueName), handlerClass.kotlin)
    }

    /**
     * Returns a module that registers a handler for an SQS queue.
     */
    @JvmStatic
    fun <T : JobHandler> create(
      queueNames: List<QueueName>,
      handlerClass: KClass<T>
    ): AwsSqsJobHandlerModule<T> {
      return AwsSqsJobHandlerModule(queueNames, handlerClass)
    }
  }
}

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val consumer: SqsJobConsumer,
  private val consumerMapping: Map<QueuesKey, JobHandler>
) : AbstractIdleService() {
  override fun startUp() {
    consumerMapping.forEach { consumer.subscribe(it.key.names, it.value) }
  }

  override fun shutDown() {}
}
