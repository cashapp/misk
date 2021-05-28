package misk.aws2.dynamodb

/**
 * A table that must be available in the DynamoDB instance. If this table doesn't exist, the service
 * will not start up.
 *
 * The table name is sometimes prefixed with the service name, like "urlshortener.urls".
 */
data class RequiredDynamoDbTable(
  val name: String
)
