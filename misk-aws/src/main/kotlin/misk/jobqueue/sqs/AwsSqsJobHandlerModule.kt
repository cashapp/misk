package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue.
 */
class AwsSqsJobHandlerModule<T : JobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>,
  private val dependsOn: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
    install(ServiceModule(AwsSqsJobHandlerSubscriptionService::class.toKey(),
        dependsOn + listOf(SqsJobConsumer::class.toKey())))
  }

  companion object {
    inline fun <reified T : JobHandler> create(
      queueName: QueueName,
      dependsOn: List<Key<out Service>> = emptyList()
    ):
        AwsSqsJobHandlerModule<T> = create(queueName, T::class, dependsOn)

    @JvmStatic
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>
    ): AwsSqsJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin)
    }

    /**
     * Returns a module that registers a handler for an SQS queue.
     */
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>,
      dependsOn: List<Key<out Service>> = emptyList()
    ): AwsSqsJobHandlerModule<T> {
      return AwsSqsJobHandlerModule(queueName, handlerClass, dependsOn)
    }
  }
}

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val consumer: SqsJobConsumer,
  private val consumerMapping: Map<QueueName, JobHandler>
) : AbstractIdleService() {
  override fun startUp() {
    consumerMapping.forEach { consumer.subscribe(it.key, it.value) }
  }

  override fun shutDown() {}
}