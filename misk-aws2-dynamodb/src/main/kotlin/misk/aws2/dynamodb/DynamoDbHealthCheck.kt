package misk.aws2.dynamodb

import javax.inject.Inject
import javax.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest
import wisp.logging.getLogger

@Singleton
class DynamoDbHealthCheck @Inject constructor(
  private val dynamoDb: DynamoDbClient
) : HealthCheck {
  override fun status(): HealthStatus {
    try {
      dynamoDb.listTables(ListTablesRequest.builder()
        .limit(3)
        .build())
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
