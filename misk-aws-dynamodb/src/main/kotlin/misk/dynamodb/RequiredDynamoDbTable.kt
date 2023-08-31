package misk.dynamodb

import misk.healthchecks.HealthCheck

/**
 * A table that must be available in the DynamoDB instance. If this table doesn't exist, the service
 * will not start up.
 *
 * HealthCheck can be provided for custom HealthCheck.
 *
 * The table name is sometimes prefixed with the service name, like "urlshortener.urls".
 */
data class RequiredDynamoDbTable(
  val name: String,
  val healthCheck: HealthCheck? = null
)
