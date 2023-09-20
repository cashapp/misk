package misk.aws2.dynamodb

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest

@Singleton
class RealDynamoDbService @Inject constructor(
  private val dynamoDb: DynamoDbClient,
  private val requiredTables: List<RequiredDynamoDbTable>,
) : AbstractIdleService(), DynamoDbService {
  override fun startUp() {
    for (table in requiredTables) {
      dynamoDb.describeTable(
        DescribeTableRequest.builder()
          .tableName(table.name)
          .build()
      )
    }
  }

  override fun shutDown() {
  }
}
