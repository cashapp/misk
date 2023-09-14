package misk.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.logging.getLogger

@Singleton
class DynamoDbHealthCheck @Inject constructor(
  private val dynamoDb: AmazonDynamoDB,
  private val requiredTables: List<RequiredDynamoDbTable>,
) : HealthCheck {
  override fun status(): HealthStatus {
    for (table in requiredTables) {
      try {
        if (table.healthCheckFactory != null) {
          val result = table.healthCheckFactory.create(dynamoDb).status()
          if (!result.isHealthy) {
            logger.error { "error performing DynamoDB ${table.healthCheckFactory::class.simpleName ?: "custom"} health check for ${table.name}" }
            return result
          }
        } else {
          dynamoDb.describeTable(table.name)
        }
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
