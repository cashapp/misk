package misk.jobqueue.sqs

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.jobqueue.BatchJobHandler
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import kotlin.reflect.KClass

/**
 * Install this module to register a batch handler for an SQS queue,
 * and if specified, registers its corresponding retry queue.
 */
class AwsSqsBatchJobHandlerModule<T : BatchJobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>,
  private val installRetryQueue: Boolean,
  private val dependsOn: List<Key<out Service>>,
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, BatchJobHandler>().addBinding(queueName).to(handler.java)
    newMapBinder<QueueName, JobHandler>()

    if (installRetryQueue) {
      newMapBinder<QueueName, BatchJobHandler>().addBinding(queueName.retryQueue).to(handler.java)
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
    inline fun <reified T : BatchJobHandler> create(
      queueName: QueueName,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsBatchJobHandlerModule<T> = create(queueName, T::class, installRetryQueue, dependsOn)

    @JvmStatic @JvmOverloads
    fun <T : BatchJobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsBatchJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin, installRetryQueue, dependsOn)
    }

    /**
     * Returns a module that registers a batch handler for an SQS queue.
     */
    @JvmOverloads
    fun <T : BatchJobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsBatchJobHandlerModule<T> {
      return AwsSqsBatchJobHandlerModule(queueName, handlerClass, installRetryQueue, dependsOn)
    }
  }
}
