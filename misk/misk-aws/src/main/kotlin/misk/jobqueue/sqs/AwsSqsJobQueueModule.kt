package misk.jobqueue.sqs

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.cloud.aws.AwsClientConfiguration
import misk.cloud.aws.AwsRegion
import misk.inject.KAbstractModule
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobQueue
import misk.jobqueue.TransactionalJobQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** [AwsSqsJobQueueModule] installs job queue support provided by SQS */
class AwsSqsJobQueueModule : KAbstractModule() {
  override fun configure() {
    requireBinding(AWSCredentialsProvider::class.java)
    requireBinding(AwsRegion::class.java)
    bind<JobConsumer>().to<SqsJobConsumer>()
    bind<JobQueue>().to<SqsJobQueue>()
    bind<TransactionalJobQueue>().to<SqsTransactionalJobQueue>()
    multibind<Service>().to<SqsJobConsumer>()
  }

  @Provides @Singleton
  fun provideSQSClient(
      region: AwsRegion,
      credentials: AWSCredentialsProvider,
      clientConfiguration: AwsClientConfiguration): AmazonSQS {
    val sqsClientBuilder = AmazonSQSClientBuilder.standard()
        .withCredentials(credentials)
        .withRegion(region.name)

    clientConfiguration.clientConfiguration.let { sqsClientBuilder.withClientConfiguration(it) }

    return sqsClientBuilder.build()
  }

  @Provides @ForSqsConsumer @Singleton
  fun provideSqsConsumerDispatchPool(): ExecutorService {
    val threadFactory = ThreadFactoryBuilder().setNameFormat("sqs-consumer-%d").build()
    return Executors.newCachedThreadPool(threadFactory)
  }
}