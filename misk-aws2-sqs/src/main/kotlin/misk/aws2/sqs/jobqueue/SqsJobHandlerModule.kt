package misk.aws2.sqs.jobqueue

import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import kotlin.reflect.KClass

/**
 * Install this module to register a handler for an SQS queue
 */
@ExperimentalMiskApi
class SqsJobHandlerModule private constructor(
  private val config: SqsConfig,
  private val queueName: QueueName,
  private val handler: KClass<out JobHandler>,
) : KAbstractModule() {
  override fun configure() {
    bind<SqsConfig>().toInstance(config)
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)

    install(ServiceModule<SubscriptionService>().dependsOn<ReadyService>())
  }

  companion object {
    inline fun <reified T: JobHandler> create(queueName: String, sqsConfig: SqsConfig = SqsConfig()): SqsJobHandlerModule {
      return create(QueueName(queueName), T::class, sqsConfig)
    }

    inline fun <reified T: JobHandler> create(queueName: QueueName, sqsConfig: SqsConfig = SqsConfig()): SqsJobHandlerModule {
      return create(queueName, T::class, sqsConfig)
    }

    fun create(queueName: QueueName, handler: KClass<out JobHandler>, sqsConfig: SqsConfig = SqsConfig()) = SqsJobHandlerModule(sqsConfig, queueName, handler)
  }
}
