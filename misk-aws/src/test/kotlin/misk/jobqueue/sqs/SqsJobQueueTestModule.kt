package misk.jobqueue.sqs

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQS
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.inject.KAbstractModule
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
    install(
        Modules.override(
            AwsSqsJobQueueModule(AwsSqsJobQueueConfig()))
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
  }
}