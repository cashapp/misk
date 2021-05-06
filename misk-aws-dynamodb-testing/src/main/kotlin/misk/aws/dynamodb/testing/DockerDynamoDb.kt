package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import misk.testing.ExternalDependency
import wisp.containers.Composer
import wisp.containers.Container
import wisp.logging.getLogger

/**
 * A test DynamoDb Local service. Tests can connect to the service at 127.0.0.1:<random_port>.
 * Use endpointConfiguration to get the service endpoint address:
 *  DockerDynamoDb.endpointConfiguration.serviceEndpoint
 */
object DockerDynamoDb : ExternalDependency {
  private val logger = getLogger<DockerDynamoDb>()

  private val pid = ProcessHandle.current().pid()

  internal val localDynamoDb = LocalDynamoDb()

  override val id = "dynamodb-local-$pid"

  private val url = localDynamoDb.url

  val awsCredentialsProvider = localDynamoDb.awsCredentialsProvider

  val endpointConfiguration = localDynamoDb.endpointConfiguration

  private val composer = Composer(
    "e-$id",
    Container {
      // DynamoDB Local listens on port 8000 by default.
      val exposedClientPort = ExposedPort.tcp(8000)
      val portBindings =
        Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(url.port)) }
      withImage("amazon/dynamodb-local")
        .withName(id)
        .withExposedPorts(exposedClientPort)
        .withCmd("-jar", "DynamoDBLocal.jar", "-sharedDb")
        .withPortBindings(portBindings)
    }
  )

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

  fun connect(): AmazonDynamoDB = localDynamoDb.connect()

  fun connectToStreams(): AmazonDynamoDBStreams = localDynamoDb.connectToStreams()
}

fun main(args: Array<String>) {
  DockerDynamoDb.startup()
}
