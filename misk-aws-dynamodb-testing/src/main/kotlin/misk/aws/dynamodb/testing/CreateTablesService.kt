package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.google.common.util.concurrent.AbstractIdleService
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateTablesService @Inject constructor(
  private val dynamoDbClient: AmazonDynamoDB,
  private val tables: Set<DynamoDbTable>
) : AbstractIdleService() {

  override fun startUp() {
    // Cleans up the tables before each run.
    for (tableName in dynamoDbClient.listTables().tableNames) {
      dynamoDbClient.deleteTable(tableName)
    }

    for (table in tables) {
      dynamoDbClient.createTable(table)
    }
  }

  override fun shutDown() {
    dynamoDbClient.shutdown()
  }

  private fun AmazonDynamoDB.createTable(
    table: DynamoDbTable
  ) {
    var tableRequest = DynamoDBMapper(this)
      .generateCreateTableRequest(table.tableClass.java)
      // Provisioned throughput needs to be specified when creating the table. However,
      // DynamoDB Local ignores your provisioned throughput settings. The values that you specify
      // when you call CreateTable and UpdateTable have no effect. In addition, DynamoDB Local
      // does not throttle read or write activity.
      .withProvisionedThroughput(ProvisionedThroughput(1L, 1L))
    val globalSecondaryIndexes = tableRequest.globalSecondaryIndexes ?: emptyList()
    for (globalSecondaryIndex in globalSecondaryIndexes) {
      // Provisioned throughput needs to be specified when creating the table.
      globalSecondaryIndex.provisionedThroughput = ProvisionedThroughput(1L, 1L)
    }
    tableRequest = table.configureTable(tableRequest)

    DynamoDB(this).createTable(tableRequest).waitForActive()
  }

  companion object {
    val CONFIGURE_TABLE_NOOP: (CreateTableRequest) -> CreateTableRequest = {
      it
    }
  }
}

private val logger = getLogger<CreateTablesService>()
