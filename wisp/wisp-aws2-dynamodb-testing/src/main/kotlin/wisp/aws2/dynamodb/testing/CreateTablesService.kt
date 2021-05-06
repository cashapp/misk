package wisp.aws2.dynamodb.testing

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput

class CreateTablesService constructor(
  private val dynamoDbClient: DynamoDbClient,
  private val tables: Set<DynamoDbTable>
) {

  fun startUp() {
    // Cleans up the tables before each run.
    for (tableName in dynamoDbClient.listTables().tableNames()) {
      dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
    }

    for (table in tables) {
      dynamoDbClient.createTable(table)
    }
  }

  fun shutDown() {
    dynamoDbClient.close()
  }

  private fun DynamoDbClient.createTable(
    table: DynamoDbTable
  ) {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDbClient)
      .build()
    var tableRequest = CreateTableEnhancedRequest.builder()
      // Provisioned throughput needs to be specified when creating the table. However,
      // DynamoDB Local ignores your provisioned throughput settings. The values that you specify
      // when you call CreateTable and UpdateTable have no effect. In addition, DynamoDB Local
      // does not throttle read or write activity.
      .provisionedThroughput(
        ProvisionedThroughput
          .builder()
          .readCapacityUnits(1L)
          .writeCapacityUnits(1L)
          .build()
      )
    tableRequest = table.configureTable(tableRequest)
    enhancedClient.table(table.tableName, TableSchema.fromClass(table.tableClass.java))
      .createTable(tableRequest.build())
  }

}
