package misk.jobqueue.sqs

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class CrossAccountJobQueueTest {
  @MiskTestModule private val module = TestModule()

  @Inject lateinit var queueResolver: QueueResolver

  @Test fun resolveCrossAccountQueue() {
    val queue = queueResolver[QueueName("my-queue")]
    assertThat(queue.sqsQueueName).isEqualTo("cross-account-test-queue")
  }

  private class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(MockTracingBackendModule())
      install(AwsSqsJobQueueModule(AwsSqsJobQueueConfig(
          external_queues = mapOf(
              "my-queue" to AwsSqsQueueConfig(
                  region = "<fill-in-here>",
                  sqs_queue_name = "cross-account-test-queue",
                  account_id = "<fill-in-here>"
              )
          )
      )))
      bind<String>().annotatedWith<AppName>().toInstance("<fill-in-here>")
      bind<AwsAccountId>().toInstance(AwsAccountId("<fill-in-here>"))
      bind<AwsRegion>().toInstance(AwsRegion("<fill-in-here>"))
    }

    @Provides @Singleton
    fun provideSTSClient(region: AwsRegion): AWSSecurityTokenService {
      val longLivedCredentials = object : AWSCredentialsProvider {
        override fun refresh() {}
        override fun getCredentials(): AWSCredentials {
          return BasicAWSCredentials("<fill-in-here>",
              "<fill-in-here>")
        }
      }

      return AWSSecurityTokenServiceClient.builder()
          .withCredentials(longLivedCredentials)
          .withRegion(region.name)
          .build()
    }

    @Provides @Singleton
    fun provideAwsCredentials(
      @AppName appName: String,
      accountId: AwsAccountId,
      sts: AWSSecurityTokenService
    ): AWSCredentialsProvider {
      val roleArn = "arn:aws:iam::${accountId.value}:role/$appName"
      return STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, appName)
          .withRoleSessionDurationSeconds(30 * 60) // 30 minutes
          .withStsClient(sts)
          .build()
    }

  }
}