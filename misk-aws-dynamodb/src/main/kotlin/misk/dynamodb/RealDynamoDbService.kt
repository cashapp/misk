package misk.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
@Deprecated(
  message =
    "AWS SDK v1 DynamoDB is deprecated. Use the AWS SDK v2 DynamoDB module in " +
      "misk-aws2-dynamodb (misk.aws2.dynamodb.RealDynamoDbService) instead."
)
class RealDynamoDbService
@Inject
constructor(private val dynamoDb: AmazonDynamoDB, private val requiredTables: List<RequiredDynamoDbTable>) :
  AbstractIdleService(), DynamoDbService {
  override fun startUp() {
    for (table in requiredTables) {
      dynamoDb.describeTable(table.name)
    }
  }

  override fun shutDown() {}
}
