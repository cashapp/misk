package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue,
 * and if specified, registers its corresponding retry queue.
 */
class AwsSqsJobHandlerModule<T : JobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>,
  private val installRetryQueue: Boolean
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)

    if (installRetryQueue) {
      newMapBinder<QueueName, JobHandler>().addBinding(queueName.retryQueue).to(handler.java)
    }

    install(ServiceModule<AwsSqsJobHandlerSubscriptionService>())
  }

  companion object {
    @JvmOverloads
    inline fun <reified T : JobHandler> create(
      queueName: QueueName,
      installRetryQueue: Boolean = true
    ): AwsSqsJobHandlerModule<T> = create(queueName, T::class, installRetryQueue)

    @JvmStatic @JvmOverloads
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>,
      installRetryQueue: Boolean = true
    ): AwsSqsJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin, installRetryQueue)
    }

    /**
     * Returns a module that registers a handler for an SQS queue.
     */
    @JvmOverloads
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>,
      installRetryQueue: Boolean = true
    ): AwsSqsJobHandlerModule<T> {
      return AwsSqsJobHandlerModule(queueName, handlerClass, installRetryQueue)
    }
  }
}

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val attributeImporter: AwsSqsQueueAttributeImporter,
  private val consumer: SqsJobConsumer,
  private val consumerMapping: Map<QueueName, JobHandler>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>
) : AbstractIdleService() {
  override fun startUp() {
    consumerMapping.forEach { consumer.subscribe(it.key, it.value) }
    externalQueues.forEach { attributeImporter.import(it.key) }
  }

  override fun shutDown() {}
}
