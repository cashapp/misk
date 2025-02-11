package misk.aws2.sqs.jobqueue

import com.google.inject.Provides
import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.cloud.aws.AwsRegion
import misk.inject.KAbstractModule
import misk.jobqueue.v2.JobConsumer
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder
import java.net.URI

@ExperimentalMiskApi
open class SqsJobQueueModule @JvmOverloads constructor(
  private val configureClient: SqsAsyncClientBuilder.() -> Unit = {}
) : KAbstractModule() {
  override fun configure() {
    requireBinding<AwsCredentialsProvider>()
    requireBinding<AwsRegion>()
    install(ServiceModule<SqsJobConsumer>().dependsOn<ReadyService>())
    bind<JobConsumer>().to<SqsJobConsumer>()
  }

  @Provides
  fun sqsAsyncClient(
    credentialsProvider: AwsCredentialsProvider,
    awsRegion: AwsRegion,
  ): SqsAsyncClient {
    val builder = SqsAsyncClient.builder()
      .credentialsProvider(credentialsProvider)
      .region(Region.of(awsRegion.name))

    builder.configureClient()
    return builder.build()
  }
}
