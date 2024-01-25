package misk.jobqueue.sqs

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.jobqueue.AbstractJobHandler
import misk.jobqueue.QueueName
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue,
 * and if specified, registers its corresponding retry queue.
 */
class AwsSqsJobHandlerModule<T : AbstractJobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>,
  private val installRetryQueue: Boolean,
  private val dependsOn: List<Key<out Service>>,
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, AbstractJobHandler>().addBinding(queueName).to(handler.java)

    if (installRetryQueue) {
      newMapBinder<QueueName, AbstractJobHandler>().addBinding(queueName.retryQueue).to(handler.java)
    }

    install(
      ServiceModule(
        key = AwsSqsJobHandlerSubscriptionService::class.toKey(),
        dependsOn = dependsOn
      ).dependsOn<ReadyService>()
    )
  }

  companion object {
    @JvmOverloads
    inline fun <reified T : AbstractJobHandler> create(
      queueName: QueueName,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsJobHandlerModule<T> = create(queueName, T::class, installRetryQueue, dependsOn)

    @JvmStatic @JvmOverloads
    fun <T : AbstractJobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin, installRetryQueue, dependsOn)
    }

    /**
     * Returns a module that registers a handler for an SQS queue.
     */
    @JvmOverloads
    fun <T : AbstractJobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsJobHandlerModule<T> {
      return AwsSqsJobHandlerModule(queueName, handlerClass, installRetryQueue, dependsOn)
    }
  }
}

@Singleton
internal class AwsSqsJobHandlerSubscriptionService @Inject constructor(
  private val attributeImporter: AwsSqsQueueAttributeImporter,
  private val consumer: SqsJobConsumer,
  private val consumerMapping: Map<QueueName, AbstractJobHandler>,
  private val externalQueues: Map<QueueName, AwsSqsQueueConfig>,
  private val config: AwsSqsJobQueueConfig
) : AbstractIdleService() {
  override fun startUp() {
    consumerMapping.forEach { consumer.subscribe(it.key, it.value) }
    externalQueues.forEach { attributeImporter.import(it.key) }
  }

  override fun shutDown() {
    if (config.safe_shutdown) {
      consumerMapping.forEach { consumer.unsubscribe(it.key) }
      attributeImporter.shutDown()
      consumer.shutDown()
    }
  }
}
