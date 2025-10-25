package misk.aws2.dynamodb

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.inject.keyOf
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import kotlin.reflect.KClass

@Singleton
class RealDynamoDbService : AbstractIdleService, DynamoDbService {
  private val dynamoDb: DynamoDbClient
  private val requiredTables: List<RequiredDynamoDbTable>

  constructor(
    injector: Injector,
    requiredTables: List<RequiredDynamoDbTable>,
    qualifier: KClass<out Annotation>? = null,
  ) {
    this.requiredTables = requiredTables
    this.dynamoDb = injector.getInstance(keyOf<DynamoDbClient>(qualifier))
  }

  // Backward-compatible constructor (unqualified)
  @Inject
  constructor(
    dynamoDb: DynamoDbClient,
    requiredTables: List<RequiredDynamoDbTable>,
  ) {
    this.dynamoDb = dynamoDb
    this.requiredTables = requiredTables
  }

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
