package misk.aws2.sqs.jobqueue

import com.google.inject.Provides
import com.google.inject.multibindings.OptionalBinder
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.cloud.aws.AwsRegion
import misk.feature.DynamicConfig
import misk.inject.DefaultAsyncSwitchModule
import misk.inject.KAbstractModule
import misk.jobqueue.v2.JobConsumer
import misk.jobqueue.v2.JobEnqueuer
import misk.testing.TestFixture
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder

open class SqsJobQueueModule
@JvmOverloads
constructor(private val config: SqsConfig, private val configureClient: SqsAsyncClientBuilder.() -> Unit = {}) :
  KAbstractModule() {
  override fun configure() {
    requireBinding<AwsCredentialsProvider>()
    requireBinding<AwsRegion>()
    bind<JobConsumer>().to<SqsJobConsumer>()
    bind<JobEnqueuer>().to<SqsJobEnqueuer>()
    multibind<TestFixture>().to<SqsJobConsumer>()

    install(DefaultAsyncSwitchModule())
    install(ServiceModule<SqsJobConsumer>().dependsOn<ReadyService>())
    bind<SqsBatchManagerFactory>().to<RealSqsBatchManagerFactory>()
    install(ServiceModule<RealSqsBatchManagerFactory>())

    // DynamicConfig is optional - only required if config_feature_flag is configured in SqsConfig
    OptionalBinder.newOptionalBinder(binder(), DynamicConfig::class.java)
  }

  @Provides
  fun sqsConfig(awsRegion: AwsRegion): SqsConfig {
    return if (config.all_queues.region != null) {
      config
    } else {
      config.copy(all_queues = config.all_queues.copy(region = awsRegion.name))
    }
  }

  @Provides
  @Singleton
  fun sqsClientFactory(credentialsProvider: AwsCredentialsProvider): SqsClientFactory =
    RealSqsClientFactory(credentialsProvider, configureClient)

  @Provides
  @Singleton
  fun sqsBatchManagerFactory(sqsClientFactory: SqsClientFactory, sqsConfig: SqsConfig): RealSqsBatchManagerFactory {
    return RealSqsBatchManagerFactory(sqsClientFactory, sqsConfig)
  }
}
