package misk.jobqueue.sqs

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQS
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.clustering.fake.lease.FakeLeaseModule
import misk.feature.testing.FakeFeatureFlagsModule
import misk.inject.KAbstractModule
import misk.tasks.RepeatedTaskQueueConfig
import misk.testing.MockTracingBackendModule

class SqsJobQueueTestModule(
  private val credentials: AWSCredentialsProvider,
  private val client: AmazonSQS
) : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(MockTracingBackendModule())
    install(AwsEnvironmentModule())
    install(FakeAwsEnvironmentModule())
    install(FakeLeaseModule())
    install(FakeFeatureFlagsModule().withOverrides {
      override(SqsJobConsumer.CONSUMERS_PER_QUEUE, 5)
      override(SqsJobConsumer.POD_CONSUMERS_PER_QUEUE, -1)
    })
    install(
        Modules.override(
            AwsSqsJobQueueModule(
                AwsSqsJobQueueConfig(
                    task_queue = RepeatedTaskQueueConfig(default_jitter_ms = 0),
                    queue_attribute_importer_frequency_ms = 0)))
            .with(SqsTestModule(credentials, client))
    )
  }
}

class SqsTestModule(
  private val credentials: AWSCredentialsProvider,
  private val client: AmazonSQS
) : KAbstractModule() {
  override fun configure() {
    bind<AWSCredentialsProvider>().toInstance(credentials)
    bind<AmazonSQS>().toInstance(client)
    bind<AmazonSQS>().annotatedWith<ForSqsReceiving>().toInstance(client)
  }
}