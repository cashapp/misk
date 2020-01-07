package misk.jobqueue.sqs

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ServiceModule
import misk.cloud.aws.AwsRegion
import misk.clustering.lease.LeaseManager
import misk.concurrent.ExecutorServiceModule
import misk.feature.FeatureFlags
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.TransactionalJobQueue
import misk.tasks.RepeatedTaskQueue
import misk.tasks.RepeatedTaskQueueConfig
import misk.tasks.RepeatedTaskQueueFactory
import javax.inject.Inject

/** [AwsSqsJobQueueModule] installs job queue support provided by SQS. */
class AwsSqsJobQueueModule(
  private val config: AwsSqsJobQueueConfig
) : KAbstractModule() {
  override fun configure() {
    requireBinding<AWSCredentialsProvider>()
    requireBinding<AwsRegion>()
    requireBinding<LeaseManager>()
    requireBinding<FeatureFlags>()

    bind<AwsSqsJobQueueConfig>().toInstance(config)

    bind<JobConsumer>().to<SqsJobConsumer>()
    bind<JobQueue>().to<SqsJobQueue>()
    bind<TransactionalJobQueue>().to<SqsTransactionalJobQueue>()

    install(ServiceModule(keyOf<RepeatedTaskQueue>(ForSqsHandling::class)))

    // We use an unbounded thread pool for the number of consumers, as we want to process
    // the messages received as fast a possible.
    install(ExecutorServiceModule.withUnboundThreadPool(
        ForSqsHandling::class,
        "sqs-consumer-%d"))

    // We use an unbounded thread pool for number of receivers, as this will be controlled dynamically
    // using a feature flag.
    install(ExecutorServiceModule.withUnboundThreadPool(
        ForSqsReceiving::class,
        "sqs-receiver"))

    // Bind a map of AmazonSQS clients for each external region that we need to contact
    val regionSpecificClientBinder = newMapBinder<AwsRegion, AmazonSQS>()
    config.external_queues
        .mapNotNull { (_, config) -> config.region }
        .map { AwsRegion(it) }
        .distinct()
        .forEach {
          regionSpecificClientBinder.addBinding(it).toProvider(AmazonSQSProvider(it))
        }

    // Bind the configs for external queues
    val externalQueueConfigBinder = newMapBinder<QueueName, AwsSqsQueueConfig>()
    config.external_queues.map { QueueName(it.key) to it.value }.forEach { (queueName, config) ->
      externalQueueConfigBinder.addBinding(queueName).toInstance(config)
    }
  }

  @Provides @Singleton
  fun provideSQSClient(region: AwsRegion, credentials: AWSCredentialsProvider): AmazonSQS {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(credentials)
        .withRegion(region.name)
        .build()
  }

  @Provides @ForSqsHandling @Singleton
  fun consumerRepeatedTaskQueue(
    queueFactory: RepeatedTaskQueueFactory,
    config: AwsSqsJobQueueConfig
  ): RepeatedTaskQueue {
    return queueFactory.new(
        "sqs-consumer-poller",
        repeatedTaskQueueConfig(config))
  }

  private fun repeatedTaskQueueConfig(config: AwsSqsJobQueueConfig) =
      config.task_queue ?: RepeatedTaskQueueConfig(num_parallel_tasks = -1)

  private class AmazonSQSProvider(val region: AwsRegion) : Provider<AmazonSQS> {
    @Inject lateinit var credentials: AWSCredentialsProvider

    override fun get() = AmazonSQSClientBuilder.standard()
        .withCredentials(credentials)
        .withRegion(region.name)
        .build()
  }
}
