package misk.jobqueue.sqs

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.buffered.QueueBufferConfig
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ServiceModule
import misk.cloud.aws.AwsRegion
import misk.clustering.lease.LeaseManager
import misk.concurrent.ExecutorServiceModule
import misk.config.AppName
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
          regionSpecificClientBinder.addBinding(it).toProvider(AmazonSQSProvider(config, it, false))
        }
    val regionSpecificClientBinderForReceiving = newMapBinder<AwsRegion, AmazonSQS>(ForSqsReceiving::class)
    config.external_queues
        .mapNotNull { (_, config) -> config.region }
        .map { AwsRegion(it) }
        .distinct()
        .forEach {
          regionSpecificClientBinderForReceiving.addBinding(it).toProvider(AmazonSQSProvider(config, it, true))
        }

    // Bind the configs for external queues
    val externalQueueConfigBinder = newMapBinder<QueueName, AwsSqsQueueConfig>()
    config.external_queues.map { QueueName(it.key) to it.value }.forEach { (queueName, config) ->
      externalQueueConfigBinder.addBinding(queueName).toInstance(config)
    }
  }

  @Provides @Singleton
  fun provideSQSClient(
    @AppName appName: String,
    region: AwsRegion,
    credentials: AWSCredentialsProvider,
    features: FeatureFlags
  ): AmazonSQS {
    return buildClient(appName, config, credentials, region, features)
  }

  @Provides @Singleton @ForSqsReceiving
  fun provideSQSClientForReceiving(region: AwsRegion, credentials: AWSCredentialsProvider): AmazonSQS {
    return buildReceivingClient(credentials, region)
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

  private class AmazonSQSProvider(
    val config: AwsSqsJobQueueConfig,
    val region: AwsRegion,
    val forSqsReceiving: Boolean
  ) : Provider<AmazonSQS> {
    @Inject lateinit var credentials: AWSCredentialsProvider
    @Inject lateinit var features: FeatureFlags
    @Inject @AppName lateinit var appName: String

    override fun get() : AmazonSQS {
      return if (forSqsReceiving) {
        buildReceivingClient(credentials, region)
      } else {
        buildClient(appName, config, credentials, region, features)
      }
    }
  }

  companion object {
    /** Build an unbuffered [AmazonSQS] client for receiving messages. */
    private fun buildReceivingClient(
      credentials: AWSCredentialsProvider,
      region: AwsRegion
    ) : AmazonSQS {
      // We don't need any buffering functionality for receiving; build a regular sync client.
      return AmazonSQSClientBuilder.standard()
        .withCredentials(credentials)
        .withRegion(region.name)
        .withClientConfiguration(ClientConfiguration()
          .withSocketTimeout(25_000)
          .withConnectionTimeout(1_000)
          // Do not artificially constrain the # of connections to SQS. Instead we rely on higher
          // level resource limiting knobs (e.g # of parallel receivers).
          // We only do this for receiving, as sending does not have equivalent knobs.
          .withMaxConnections(Int.MAX_VALUE))
        .build()
    }

    /** Build a buffered [AmazonSQS] client for sending and deleting messages (job enqueue & ack respectively). */
    private fun buildClient(
      appName: String,
      config: AwsSqsJobQueueConfig,
      credentials: AWSCredentialsProvider,
      region: AwsRegion,
      features: FeatureFlags
    ): AmazonSQS {
      // Use a buffered client for sending, to group enqueues and deletes into batches.
      // The trade-off is fewer network calls (which costs less $ since SQS cost is directly
      // proportional to API calls) at a slightly higher individual call duration.
      // Every call individual SendMessage and DeleteMessage request will wait up to 200ms
      // for similar requests and sent in a batch. Batches are immediately sent once full.
      val asyncClient = AmazonSQSAsyncClientBuilder.standard()
        .withCredentials(credentials)
        .withRegion(region.name)
        .withClientConfiguration(ClientConfiguration()
          .withSocketTimeout(config.sqs_sending_socket_timeout_ms)
          .withConnectionTimeout(config.sqs_sending_connect_timeout_ms)
          .withRequestTimeout(config.sqs_sending_request_timeout_ms))
        .build()

      // NB: It's unlikely this client will be used for receiving (i.e. to issue ReceiveMessage
      // requests) but turn off pre-fetching in any case. Without pre-fetching, receives are
      // on-demand (i.e. work exactly the same as a non-buffered client) and won't spawn extra
      // background threads to pre-fill receive buffers.
      val bufferConfig = QueueBufferConfig().withNoPrefetching()
      // TODO(bruno): Replace with AmazonSQSBufferedAsyncClient(asyncClient, bufferConfig) once rollout proves safe.
      return FlaggedBufferedSqsClient(
        asyncClient,
        AmazonSQSBufferedAsyncClient(asyncClient, bufferConfig),
        appName,
        features
      )
    }
  }
}

/** Modify a [QueueBufferConfig] to disable all receive pre-fetching settings. */
fun QueueBufferConfig.withNoPrefetching() : QueueBufferConfig {
  return withMaxInflightReceiveBatches(0)
    .withAdapativePrefetching(false)
    .withMaxDoneReceiveBatches(0)
}
