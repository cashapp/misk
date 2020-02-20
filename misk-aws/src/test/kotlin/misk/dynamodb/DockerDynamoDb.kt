package misk.dynamodb

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import misk.containers.Composer
import misk.containers.Container
import misk.logging.getLogger
import misk.testing.ExternalDependency

/**
 * A test DynamoDb Service. Tests can connect to the service at 127.0.0.1:[clientPort]
 */
internal object DockerDynamoDb : ExternalDependency {

  private val log = getLogger<DockerDynamoDb>()
  private const val clientPort = 8000

  override fun beforeEach() {
    // noop
  }

  override fun afterEach() {
    cleanupTables()
  }

  private fun cleanupTables() {
    val tables = client.listTables()
    tables.tableNames.forEach { client.deleteTable(it) }
  }

  private val composer = Composer("e-dynamodb", Container {
    val exposedClientPort = ExposedPort.tcp(clientPort)
    withImage("amazon/dynamodb-local")
        .withName("peddle-dynamodb-local")
        .withExposedPorts(exposedClientPort)
        .withPortBindings(
            Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(clientPort)) })
  })

  private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
      .standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials("key", "secret")))
      .withEndpointConfiguration(
          AwsClientBuilder.EndpointConfiguration(
              "http://localhost:$clientPort",
              Regions.US_WEST_1.toString()
          )
      )
      .build()

  override fun startup() {
    composer.start()
    while (true) {
      try {
        client.deleteTable("not a table")
      } catch (e: Exception) {
        if (e is AmazonDynamoDBException) {
          break
        }
        log.info { "dynamo not available yet" }
        Thread.sleep(100)
      }
    }
    log.info { "dynamo is available" }
  }

  override fun shutdown() {
    composer.stop()
  }
}

fun main(args: Array<String>) {
  DockerDynamoDb.startup()
}
