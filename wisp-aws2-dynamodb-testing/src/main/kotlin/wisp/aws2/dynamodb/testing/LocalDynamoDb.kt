package wisp.aws2.dynamodb.testing

import okhttp3.HttpUrl
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

/**
 * A DynamoDB test server running in-process or in a local Docker container.
 */
open class LocalDynamoDb constructor(port: Int) {

  constructor() : this(pickPort())

  val url = HttpUrl.Builder()
    .scheme("http")
    .host("localhost")
    .port(port)
    .build()

  val awsCredentialsProvider: StaticCredentialsProvider = StaticCredentialsProvider.create(
    AwsBasicCredentials.create("key", "secret")
  )

  fun connect(): DynamoDbClient {
    return DynamoDbClient.builder()
      // The values that you supply for the AWS access key and the Region are only used to name
      // the database file.
      .credentialsProvider(awsCredentialsProvider)
      .region(Region.US_WEST_2)
      .endpointOverride(url.toUri())
      .build()
  }

  fun connectToStreams(): DynamoDbStreamsClient {
    return DynamoDbStreamsClient.builder()
      // The values that you supply for the AWS access key and the Region are only used to name
      // the database file.
      .credentialsProvider(awsCredentialsProvider)
      .region(Region.US_WEST_2)
      .endpointOverride(url.toUri())
      .build()
  }

  companion object {
    fun pickPort(): Int {
      // There is a tolerable chance of flaky tests caused by port collision.
      return 58000 + (ProcessHandle.current().pid() % 1000).toInt()
    }
  }
}
