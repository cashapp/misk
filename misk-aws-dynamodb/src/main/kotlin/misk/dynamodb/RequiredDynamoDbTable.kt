package misk.dynamodb

/**
 * A table that must be available in the DynamoDB instance. If this table doesn't exist, the service will not start up.
 *
 * The table name is sometimes prefixed with the service name, like "urlshortener.urls".
 */
@Deprecated(
  message =
    "AWS SDK v1 DynamoDB is deprecated. Use the AWS SDK v2 DynamoDB module in " +
      "misk-aws2-dynamodb (misk.aws2.dynamodb.RequiredDynamoDbTable) instead."
)
data class RequiredDynamoDbTable(val name: String)
