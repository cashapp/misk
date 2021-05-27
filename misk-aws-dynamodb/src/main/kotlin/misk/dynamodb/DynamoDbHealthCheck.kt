package misk.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import javax.inject.Inject
import javax.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.logging.getLogger

@Singleton
class DynamoDbHealthCheck @Inject constructor(
  private val dynamoDb: AmazonDynamoDB
) : HealthCheck {
  override fun status(): HealthStatus {
    try {
      dynamoDb.listTables(3)
      return HealthStatus.healthy("DynamoDB")
    } catch (e: Exception) {
      logger.error(e) { "error performing DynamoDB health check" }
      return HealthStatus.unhealthy("DynamoDB: failed to list table names")
    }
  }

  companion object {
    val logger = getLogger<DynamoDbHealthCheck>()
  }
}
