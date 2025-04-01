package misk.aws2.sqs.jobqueue

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder
import java.util.concurrent.ConcurrentHashMap

class SqsClientFactory @JvmOverloads constructor(
  private val credentialsProvider: AwsCredentialsProvider,
  private val configureClient: SqsAsyncClientBuilder.() -> Unit = {}
) {
  private val clients = ConcurrentHashMap<String, SqsAsyncClient>()

  fun get(region: String): SqsAsyncClient {
    return clients.computeIfAbsent(region) {
      val builder = SqsAsyncClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))

      builder.configureClient()
      builder.build()
    }
  }
}
