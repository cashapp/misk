package misk.aws.dynamodb.testing

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A DynamoDB test server running in-process or in a local Docker container.
 */
@Singleton
internal class LocalDynamoDb internal constructor(port: Int) {

  @Inject constructor() : this(pickPort())

  val url = HttpUrl.Builder()
    .scheme("http")
    .host("localhost")
    .port(port)
    .build()

  val awsCredentialsProvider: AWSCredentialsProvider = AWSStaticCredentialsProvider(
    BasicAWSCredentials("key", "secret")
  )

  val endpointConfiguration = AwsClientBuilder.EndpointConfiguration(
    url.toString(),
    Regions.US_WEST_2.toString()
  )

  fun connect(): AmazonDynamoDB {
    return AmazonDynamoDBClientBuilder.standard()
      // The values that you supply for the AWS access key and the Region are only used to name
      // the database file.
      .withCredentials(awsCredentialsProvider)
      .withEndpointConfiguration(endpointConfiguration)
      .build()
  }

  fun connectToStreams(): AmazonDynamoDBStreams {
    return AmazonDynamoDBStreamsClientBuilder.standard()
      .withCredentials(awsCredentialsProvider)
      .withEndpointConfiguration(endpointConfiguration)
      .build()
  }

  companion object {
    internal fun pickPort(): Int {
      // There is a tolerable chance of flaky tests caused by port collision.
      return 58000 + (ProcessHandle.current().pid() % 1000).toInt()
    }
  }
}
