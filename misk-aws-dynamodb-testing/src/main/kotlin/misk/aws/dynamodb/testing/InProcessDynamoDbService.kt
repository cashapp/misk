package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal
import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class InProcessDynamoDbService @Inject constructor(
  private val amazonDynamoDbLocal: AmazonDynamoDBLocal
) : AbstractIdleService() {
  override fun startUp() {
  }

  override fun shutDown() {
    amazonDynamoDbLocal.shutdownNow()
  }
}
