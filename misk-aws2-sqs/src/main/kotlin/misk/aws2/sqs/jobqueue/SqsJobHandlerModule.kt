package misk.aws2.sqs.jobqueue

import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue
 */
@ExperimentalMiskApi
class SqsJobHandlerModule private constructor(
  private val queueName: QueueName,
  private val handler: KClass<out JobHandler>,
  private val parallelism: Int,
  private val concurrency: Int,
  private val channelCapacity: Int,
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
    newMapBinder<QueueName, Subscription>().addBinding(queueName).toInstance(Subscription(queueName, handler, parallelism, concurrency, channelCapacity))

    install(ServiceModule<SubscriptionService>().dependsOn<ReadyService>())
  }

  companion object {
    inline fun <reified T: JobHandler> create(queueName: String, parallelism: Int = 1, concurrency: Int = 1, channelCapacity: Int = 0): SqsJobHandlerModule {
      return create(QueueName(queueName), T::class, parallelism, concurrency, channelCapacity)
    }

    inline fun <reified T: JobHandler> create(queueName: QueueName, parallelism: Int = 1, concurrency: Int = 1, channelCapacity: Int = 0): SqsJobHandlerModule {
      return create(queueName, T::class, parallelism, concurrency, channelCapacity)
    }

    fun create(queueName: QueueName, handler: KClass<out JobHandler>, parallelism: Int = 1, concurrency: Int = 1, channelCapacity: Int = 0) = SqsJobHandlerModule(queueName, handler, parallelism, concurrency, channelCapacity)
  }
}
