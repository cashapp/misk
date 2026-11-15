package misk.aws2.sqs.jobqueue

import java.util.concurrent.ConcurrentHashMap
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder

internal class RealSqsClientFactory
@JvmOverloads
constructor(
  private val credentialsProvider: AwsCredentialsProvider,
  private val configureClient: SqsAsyncClientBuilder.() -> Unit = {},
) : SqsClientFactory {
  private val clients = ConcurrentHashMap<String, SqsAsyncClient>()

  override fun get(region: String): SqsAsyncClient {
    return clients.computeIfAbsent(region) {
      SqsAsyncClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .apply(configureClient)
        .build()
    }
  }
}
