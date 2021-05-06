package wisp.aws2.dynamodb.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import wisp.containers.Composer
import wisp.containers.Container
import wisp.logging.getLogger

/**
 * A test DynamoDb Local service. Tests can connect to the service at 127.0.0.1:<random_port>.
 *
 *
 */
class DockerDynamoDb(val localDynamoDb: LocalDynamoDb = LocalDynamoDb()) {
  private val logger = getLogger<DockerDynamoDb>()

  private val pid = ProcessHandle.current().pid()

  val id = "dynamodb-local-$pid"

  private val url = localDynamoDb.url

  val awsCredentialsProvider = localDynamoDb.awsCredentialsProvider

  internal val composer = Composer(
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

  fun startup() {
    Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
    composer.start()
    val client = connect()
    while (true) {
      try {
        client.deleteTable(DeleteTableRequest.builder().tableName("not a table").build())
      } catch (e: Exception) {
        if (e is DynamoDbException) {
          break
        }
        logger.info { "DynamoDb is not available yet" }
        Thread.sleep(100)
      }
    }
    client.close()
    logger.info { "DynamoDb is available" }
  }

  fun shutdown() {
    composer.stop()
  }

  fun connect(): DynamoDbClient = localDynamoDb.connect()
  fun connectToStreams(): DynamoDbStreamsClient = localDynamoDb.connectToStreams()
}
