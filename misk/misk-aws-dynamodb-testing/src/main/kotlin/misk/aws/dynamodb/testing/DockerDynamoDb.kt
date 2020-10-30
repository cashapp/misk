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
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import misk.containers.Composer
import misk.containers.Container
import misk.logging.getLogger
import misk.testing.ExternalDependency
import okhttp3.HttpUrl

/**
 * A test DynamoDb Local service. Tests can connect to the service at 127.0.0.1:<random_port>.
 * Use endpointConfiguration to get the service endpoint address:
 *  DockerDynamoDb.endpointConfiguration.serviceEndpoint
 */
object DockerDynamoDb : ExternalDependency {
  private val logger = getLogger<DockerDynamoDb>()

  private val pid = ProcessHandle.current().pid()
  override val id = "dynamodb-local-$pid"

  private val url = HttpUrl.Builder()
      .scheme("http")
      .host("localhost")
      // There is a tolerable chance of flaky tests caused by port collision.
      .port(58000 + (pid % 1000).toInt())
      .build()

  val awsCredentialsProvider: AWSCredentialsProvider =
      AWSStaticCredentialsProvider(
          BasicAWSCredentials("key", "secret")
      )

  val endpointConfiguration = AwsClientBuilder.EndpointConfiguration(
      url.toString(),
      Regions.US_WEST_2.toString()
  )

  private val composer = Composer("e-$id", Container {
    // DynamoDB Local listens on port 8000 by default.
    val exposedClientPort = ExposedPort.tcp(8000)
    val portBindings =
        Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(url.port)) }
    withImage("amazon/dynamodb-local")
        .withName(id)
        .withExposedPorts(exposedClientPort)
        .withCmd("-jar", "DynamoDBLocal.jar", "-sharedDb")
        .withPortBindings(portBindings)
  })

  override fun beforeEach() {
    // noop
  }

  override fun afterEach() {
    // noop
  }

  override fun startup() {
    composer.start()
    val client = connect()
    while (true) {
      try {
        client.deleteTable("not a table")
      } catch (e: Exception) {
        if (e is AmazonDynamoDBException) {
          break
        }
        logger.info { "DynamoDb is not available yet" }
        Thread.sleep(100)
      }
    }
    client.shutdown()
    logger.info { "DynamoDb is available" }
  }

  override fun shutdown() {
    composer.stop()
  }

  fun connect(): AmazonDynamoDB {
    return AmazonDynamoDBClientBuilder
        .standard()
        // The values that you supply for the AWS access key and the Region are only used to name the database file.
        .withCredentials(awsCredentialsProvider)
        .withEndpointConfiguration(endpointConfiguration)
        .build()
  }

  fun connectToStreams(): AmazonDynamoDBStreams {
    return AmazonDynamoDBStreamsClientBuilder
        .standard()
        .withCredentials(awsCredentialsProvider)
        .withEndpointConfiguration(endpointConfiguration)
        .build()
  }
}

fun main(args: Array<String>) {
  DockerDynamoDb.startup()
}
