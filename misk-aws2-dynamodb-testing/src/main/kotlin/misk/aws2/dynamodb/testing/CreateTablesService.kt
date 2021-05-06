package misk.aws2.dynamodb.testing

import com.google.common.util.concurrent.AbstractIdleService
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateTablesService @Inject constructor(
  private val dynamoDbClient: DynamoDbClient,
  private val tables: Set<DynamoDbTable>
) : AbstractIdleService() {

  private val delegate: wisp.aws2.dynamodb.testing.CreateTablesService = run {
    val wispDynamoDbTables = tables.map { it.toWispDynamoDbTable() }.toSet()
    wisp.aws2.dynamodb.testing.CreateTablesService(dynamoDbClient, wispDynamoDbTables)
  }

  override fun startUp() {
    delegate.startUp()
  }

  override fun shutDown() {
    delegate.shutDown()
  }

  companion object {
    val CONFIGURE_TABLE_NOOP:
      (CreateTableEnhancedRequest.Builder) -> CreateTableEnhancedRequest.Builder =
      {
        it
      }
  }
}
