package misk.jobqueue.sqs

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.AsyncSwitch
import misk.inject.DefaultAsyncSwitchModule
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.jobqueue.BatchJobHandler
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue,
 * and if specified, registers its corresponding retry queue.
 */
class AwsSqsJobHandlerModule<T : JobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>,
  private val installRetryQueue: Boolean,
  private val dependsOn: List<Key<out Service>>,
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
    newMapBinder<QueueName, BatchJobHandler>()

    if (installRetryQueue) {
      newMapBinder<QueueName, JobHandler>().addBinding(queueName.retryQueue).to(handler.java)
    }

    // Install the subscription service module once, even if multiple handlers are registered
    // The first invocation's dependsOn parameter is used for the service
    install(AwsSqsJobHandlerSubscriptionServiceModule(dependsOn))
  }

  companion object {
    @JvmOverloads
    inline fun <reified T : JobHandler> create(
      queueName: QueueName,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsJobHandlerModule<T> = create(queueName, T::class, installRetryQueue, dependsOn)

    @JvmStatic
    @JvmOverloads
    fun <T : JobHandler> create(
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
    fun <T : JobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>,
      installRetryQueue: Boolean = true,
      dependsOn: List<Key<out Service>> = emptyList(),
    ): AwsSqsJobHandlerModule<T> {
      return AwsSqsJobHandlerModule(queueName, handlerClass, installRetryQueue, dependsOn)
    }
  }
}

/**
 * Internal module that installs the AwsSqsJobHandlerSubscriptionService.
 * This extends KInstallOnceModule so it's only configured once even if
 * multiple AwsSqsJobHandlerModule instances are installed.
 *
 * IMPORTANT: KInstallOnceModule uses class-based equality, so Guice will only
 * call configure() on the FIRST instance it encounters. This means:
 * - The first AwsSqsJobHandlerModule installation determines the dependencies
 * - Subsequent installations are skipped entirely by Guice (configure() won't be called)
 * - If you need consistent dependencies across all handlers, ensure they're all
 *   installed with the same dependsOn parameter (typically the default empty list)
 */
private class AwsSqsJobHandlerSubscriptionServiceModule(
  private val dependsOn: List<Key<out Service>>
) : KInstallOnceModule() {

  override fun configure() {
    install(DefaultAsyncSwitchModule())
    install(
      ServiceModule<AwsSqsJobHandlerSubscriptionService>()
        .conditionalOn<AsyncSwitch>("sqs")
        .dependsOn(dependsOn)
        .dependsOn<ReadyService>()
    )
  }
}
