package misk.aws2.sqs.jobqueue.coordinated

import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.cloud.aws.AwsRegion
import misk.concurrent.ExecutorServiceModule
import misk.feature.FeatureFlags
import misk.inject.DefaultAsyncSwitchModule
import misk.inject.KAbstractModule
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.TransactionalJobQueue
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueConfig
import misk.tasks.RepeatedTaskQueueFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder
import software.amazon.awssdk.services.sqs.SqsClientBuilder
import wisp.lease.LeaseManager

/** [CoordinatedAwsSqsJobQueueModule] installs coordinated job queue support provided by SQS. */
open class CoordinatedAwsSqsJobQueueModule(private val config: AwsSqsJobQueueConfig) : KAbstractModule() {
  override fun configure() {
    requireBinding<AwsCredentialsProvider>()
    requireBinding<AwsRegion>()
    requireBinding<LeaseManager>()
    requireBinding<FeatureFlags>()

    bind<AwsSqsJobQueueConfig>().toInstance(config)

    bind<JobConsumer>().to<SqsJobConsumer>()
    bind<JobQueue>().to<SqsJobQueue>()
    bind<TransactionalJobQueue>().to<SqsTransactionalJobQueue>()

    install(ExecutorServiceModule.withUnboundThreadPool(ForSqsHandling::class, "sqs-consumer-%d"))
    install(ExecutorServiceModule.withUnboundThreadPool(ForSqsReceiving::class, "sqs-receiver-%d"))

    val externalQueueConfigBinder = newMapBinder<QueueName, AwsSqsQueueConfig>()
    config.external_queues
      .map { QueueName(it.key) to it.value }
      .forEach { (queueName, config) -> externalQueueConfigBinder.addBinding(queueName).toInstance(config) }

    install(DefaultAsyncSwitchModule())
    install(ServiceModule<RepeatedTaskQueue, ForSqsHandling>().dependsOn<ReadyService>())
    install(ServiceModule<RealSqsClientFactory>())
  }

  open fun configureClient(builder: SqsClientBuilder) {}

  open fun configureClient(builder: SqsAsyncClientBuilder) {}

  @Provides
  @Singleton
  internal fun realSqsClientFactory(credentials: AwsCredentialsProvider): RealSqsClientFactory {
    return RealSqsClientFactory(config, credentials, ::configureClient, ::configureClient)
  }

  @Provides
  @Singleton
  internal fun sqsClientFactory(realSqsClientFactory: RealSqsClientFactory): SqsClientFactory = realSqsClientFactory

  @Provides
  @ForSqsHandling
  @Singleton
  fun consumerRepeatedTaskQueue(
    queueFactory: RepeatedTaskQueueFactory,
    config: AwsSqsJobQueueConfig,
  ): RepeatedTaskQueue {
    return queueFactory.new("sqs-consumer-poller", repeatedTaskQueueConfig(config))
  }

  private fun repeatedTaskQueueConfig(config: AwsSqsJobQueueConfig) =
    config.task_queue ?: RepeatedTaskQueueConfig(num_parallel_tasks = -1)
}
