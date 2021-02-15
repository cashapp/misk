package misk.aws2.dynamodb.testing

import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal
import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class InProcessDynamoDbService @Inject constructor(
  private val dynamoDBProxyServer: DynamoDBProxyServer
) : AbstractIdleService() {

  override fun startUp() {
    dynamoDBProxyServer.start()
  }

  override fun shutDown() {
    dynamoDBProxyServer.stop()
  }
}
