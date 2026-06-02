package misk.dynamodb

import com.google.common.util.concurrent.Service

/**
 * Service that's running when DynamoDb is usable. Configure your service to depend on this service if it needs
 * DynamoDb.
 */
@Deprecated(
  message =
    "AWS SDK v1 DynamoDB is deprecated. Use the AWS SDK v2 DynamoDB module in " +
      "misk-aws2-dynamodb (misk.aws2.dynamodb.DynamoDbService) instead."
)
interface DynamoDbService : Service
