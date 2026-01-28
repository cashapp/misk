package misk.aws2.sqs.jobqueue

import misk.MiskTestingServiceModule
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.inject.ReusableTestModule
import misk.jobqueue.QueueName
import misk.testing.MockTracingBackendModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region

class SqsJobHandlerTestModule(private val dockerSqs: DockerSqs, private val sqsConfig: SqsConfig) :
  ReusableTestModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(MockTracingBackendModule())

    install(AwsEnvironmentModule())
    install(FakeAwsEnvironmentModule())

    bind<AwsCredentialsProvider>().toInstance(dockerSqs.credentialsProvider)
    bind<Region>().toInstance(dockerSqs.region)

    install(SqsJobQueueModule(sqsConfig) { endpointOverride(dockerSqs.endpointUri) })
    install(SqsJobHandlerModule.create<ExampleHandler>(QueueName("test-queue-1")))
    install(SqsJobHandlerModule.create<ExampleExternalQueueHandler>(QueueName("external-test-queue")))
  }
}
