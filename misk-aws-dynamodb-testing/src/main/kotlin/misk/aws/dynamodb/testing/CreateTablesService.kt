package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException
import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class CreateTablesService @Inject constructor(
  private val dynamoDbClient: AmazonDynamoDB,
  private val tables: Set<DynamoDbTable>
) : AbstractIdleService() {

  override fun startUp() {
    // Cleans up the tables before each run.
    for (tableName in dynamoDbClient.listTables().tableNames) {
      dynamoDbClient.deleteTable(tableName)
    }

    for (table in tables) {
      dynamoDbClient.createTable(table.tableClass)
    }
  }

  override fun shutDown() {
    dynamoDbClient.shutdown()
  }

  private fun AmazonDynamoDB.createTable(
    table: KClass<*>
  ) {
    val tableRequest = DynamoDBMapper(this)
        .generateCreateTableRequest(table.java)
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
    DynamoDB(this).createTable(tableRequest).waitForActive()
  }
}

private val logger = getLogger<CreateTablesService>()

