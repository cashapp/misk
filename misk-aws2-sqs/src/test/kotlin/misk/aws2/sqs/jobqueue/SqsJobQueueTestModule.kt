package misk.aws2.sqs.jobqueue

import misk.MiskTestingServiceModule
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.cloud.aws.AwsRegion
import misk.inject.ReusableTestModule
import misk.jobqueue.QueueName
import misk.testing.MockTracingBackendModule
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region

class SqsJobQueueTestModule(
  private val dockerSqs: DockerSqs,
  private val consumptionControllers: Map<QueueName, SqsConsumptionController> = emptyMap(),
) : ReusableTestModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(MockTracingBackendModule())

    bind<AwsRegion>().toInstance(AwsRegion("us-east-1"))
    bind<AwsCredentialsProvider>().toInstance(dockerSqs.credentialsProvider)
    bind<Region>().toInstance(dockerSqs.region)
    install(SqsJobQueueModule(SqsConfig()) { endpointOverride(dockerSqs.endpointUri) })

    consumptionControllers.forEach { (queueName, controller) ->
      newMapBinder<QueueName, SqsConsumptionController>().addBinding(queueName).toInstance(controller)
    }
  }
}
