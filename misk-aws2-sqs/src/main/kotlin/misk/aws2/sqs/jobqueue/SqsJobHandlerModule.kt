package misk.aws2.sqs.jobqueue

import kotlin.reflect.KClass
import misk.ReadyService
import misk.ServiceModule
import misk.inject.AsyncSwitch
import misk.inject.DefaultAsyncSwitchModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler

/** Install this module to register a handler for an SQS queue */
class SqsJobHandlerModule
private constructor(private val queueName: QueueName, private val handler: KClass<out JobHandler>) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)

    install(DefaultAsyncSwitchModule())
    install(ServiceModule<SubscriptionService>().conditionalOn<AsyncSwitch>("sqs").dependsOn<ReadyService>())
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
