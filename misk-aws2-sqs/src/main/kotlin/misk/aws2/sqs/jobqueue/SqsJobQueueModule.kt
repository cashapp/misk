package misk.aws2.sqs.jobqueue

import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.cloud.aws.AwsRegion
import misk.inject.AsyncModule
import misk.inject.KAbstractModule
import misk.jobqueue.v2.JobConsumer
import misk.jobqueue.v2.JobEnqueuer
import misk.testing.TestFixture
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder

open class SqsJobQueueModule @JvmOverloads constructor(
  private val config: SqsConfig,
  private val configureClient: SqsAsyncClientBuilder.() -> Unit = {}
) : AsyncModule, KAbstractModule() {
  override fun configure() {
    install(CommonModule(config, configureClient))

    // TODO remove explicit inline environment variable check once AsyncModule filtering in Guice is working
    if (!System.getenv("DISABLE_ASYNC_TASKS").toBoolean()) {
      install(ServiceModule<SqsJobConsumer>().dependsOn<ReadyService>())
    }
  }

  @OptIn(ExperimentalMiskApi::class)
  override fun moduleWhenAsyncDisabled(): KAbstractModule = CommonModule(config, configureClient)

  private class CommonModule(
    private val config: SqsConfig,
    private val configureClient: SqsAsyncClientBuilder.() -> Unit
  ) : KAbstractModule() {
    override fun configure() {
      requireBinding<AwsCredentialsProvider>()
      requireBinding<AwsRegion>()
      bind<JobConsumer>().to<SqsJobConsumer>()
      bind<JobEnqueuer>().to<SqsJobEnqueuer>()
      multibind<TestFixture>().to<SqsJobConsumer>()
    }

    @Provides
    fun sqsConfig(awsRegion: AwsRegion): SqsConfig {
      return if (config.all_queues.region != null) {
        config
      } else {
        config.copy(
          all_queues = config.all_queues.copy(
            region = awsRegion.name,
          ),
        )
      }
    }

    @Provides @Singleton
    fun sqsClientFactory(
      credentialsProvider: AwsCredentialsProvider,
    ): SqsClientFactory = RealSqsClientFactory(credentialsProvider, configureClient)
  }
}
