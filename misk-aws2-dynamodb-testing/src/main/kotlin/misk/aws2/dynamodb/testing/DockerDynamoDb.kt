package misk.aws2.dynamodb.testing

import misk.testing.ExternalDependency
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

/**
 * A test DynamoDb Local service. Tests can connect to the service at 127.0.0.1:<random_port>.
 * Use endpointConfiguration to get the service endpoint address:
 *  DockerDynamoDb.endpointConfiguration.serviceEndpoint
 */
object DockerDynamoDb : ExternalDependency {
  private val delegate = wisp.aws2.dynamodb.testing.DockerDynamoDb(LocalDynamoDb())

  override val id = delegate.id
  val localDynamoDb = delegate.localDynamoDb
  val awsCredentialsProvider = delegate.awsCredentialsProvider

  override fun beforeEach() {
    // noop
  }

  override fun afterEach() {
    // noop
  }

  override fun startup() {
    delegate.startup()
  }

  override fun shutdown() {
    delegate.shutdown()
  }

  fun connect(): DynamoDbClient =
    delegate.connect()

  fun connectToStreams(): DynamoDbStreamsClient =
    delegate.connectToStreams()
}

fun main(args: Array<String>) {
  DockerDynamoDb.startup()
}
