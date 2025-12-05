package misk.aws2.sqs.jobqueue

import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.AsyncModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue
 */
class SqsJobHandlerModule private constructor(
  private val queueName: QueueName,
  private val handler: KClass<out JobHandler>,
) : AsyncModule, KAbstractModule() {
  override fun configure() {
    install(CommonModule(queueName, handler))

    // TODO remove explicit inline environment variable check once AsyncModule filtering in Guice is working
    if (!System.getenv("DISABLE_ASYNC_TASKS").toBoolean()) {
      install(ServiceModule<SubscriptionService>().dependsOn<ReadyService>())
    }
  }

  @OptIn(ExperimentalMiskApi::class)
  override fun moduleWhenAsyncDisabled(): KAbstractModule = CommonModule(queueName, handler)

  private class CommonModule(
    private val queueName: QueueName,
    private val handler: KClass<out JobHandler>,
  ) : KAbstractModule() {
    override fun configure() {
      newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
    }
  }

  companion object {
    inline fun <reified T : JobHandler> create(queueName: String): SqsJobHandlerModule {
      return create(QueueName(queueName), T::class)
    }

    inline fun <reified T : JobHandler> create(queueName: QueueName): SqsJobHandlerModule {
      return create(queueName, T::class)
    }

    fun create(queueName: QueueName, handler: KClass<out JobHandler>) = SqsJobHandlerModule(queueName, handler)
  }
}
