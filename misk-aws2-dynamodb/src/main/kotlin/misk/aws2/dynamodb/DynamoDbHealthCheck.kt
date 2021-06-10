package misk.aws2.dynamodb

import javax.inject.Inject
import javax.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import wisp.logging.getLogger

@Singleton
class DynamoDbHealthCheck @Inject constructor(
  private val dynamoDb: DynamoDbClient,
  private val requiredTables: List<RequiredDynamoDbTable>,
) : HealthCheck {
  override fun status(): HealthStatus {
    for (table in requiredTables) {
      try {
        dynamoDb.describeTable(DescribeTableRequest.builder()
          .tableName(table.name)
          .build())
      } catch (e: Exception) {
        logger.error(e) { "error performing DynamoDB health check for ${table.name}" }
        return HealthStatus.unhealthy("DynamoDB: failed to describe ${table.name}")
      }
    }
    return HealthStatus.healthy("DynamoDB")
  }

  companion object {
    val logger = getLogger<DynamoDbHealthCheck>()
  }
}
