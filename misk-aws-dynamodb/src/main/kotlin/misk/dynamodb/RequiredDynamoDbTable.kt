package misk.dynamodb

/**
 * A table that must be available in the DynamoDB instance. If this table doesn't exist, the service
 * will not start up.
 *
 * [healthCheckFactory] can be provided for custom HealthCheck.
 *
 * The table name is sometimes prefixed with the service name, like "urlshortener.urls".
 */
data class RequiredDynamoDbTable(
  val name: String,
  val healthCheckFactory: DynamoDBHealthCheckFactory? = null
)
