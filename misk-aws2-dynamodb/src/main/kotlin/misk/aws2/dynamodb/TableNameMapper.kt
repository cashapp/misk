package misk.aws2.dynamodb

import java.util.function.Consumer
import software.amazon.awssdk.enhanced.dynamodb.Document
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedResponse

/**
 * Mapping dynamodb table names at runtime. Used in parallel tests for using isolated tables per parallel test process.
 */
interface TableNameMapper {
  fun mapName(tableName: String): String = tableName
}

fun DynamoDbEnhancedClient.withTableNameMapper(tableNameMapper: TableNameMapper): DynamoDbEnhancedClient =
  MappedDynamoDbEnhancedClient(client = this, tableNameMapper = tableNameMapper)

internal class MappedDynamoDbEnhancedClient(
  private val client: DynamoDbEnhancedClient,
  private val tableNameMapper: TableNameMapper,
) : DynamoDbEnhancedClient {

  override fun <T : Any?> table(name: String?, schema: TableSchema<T>?): DynamoDbTable<T> =
    client.table(name?.let { tableNameMapper.mapName(it) }, schema)

  override fun transactWriteItems(request: TransactWriteItemsEnhancedRequest): Void? {
    return client.transactWriteItems(request)
  }

  override fun transactWriteItems(requestConsumer: Consumer<TransactWriteItemsEnhancedRequest.Builder>?): Void? {
    return client.transactWriteItems(requestConsumer)
  }

  override fun batchGetItem(request: BatchGetItemEnhancedRequest?): BatchGetResultPageIterable {
    return client.batchGetItem(request)
  }

  override fun batchGetItem(
    requestConsumer: Consumer<BatchGetItemEnhancedRequest.Builder>?
  ): BatchGetResultPageIterable {
    return client.batchGetItem(requestConsumer)
  }

  override fun batchWriteItem(request: BatchWriteItemEnhancedRequest?): BatchWriteResult {
    return client.batchWriteItem(request)
  }

  override fun batchWriteItem(requestConsumer: Consumer<BatchWriteItemEnhancedRequest.Builder>?): BatchWriteResult {
    return client.batchWriteItem(requestConsumer)
  }

  override fun transactGetItems(request: TransactGetItemsEnhancedRequest?): MutableList<Document> {
    return client.transactGetItems(request)
  }

  override fun transactGetItems(
    requestConsumer: Consumer<TransactGetItemsEnhancedRequest.Builder>?
  ): MutableList<Document> {
    return client.transactGetItems(requestConsumer)
  }

  override fun transactWriteItemsWithResponse(
    request: TransactWriteItemsEnhancedRequest?
  ): TransactWriteItemsEnhancedResponse {
    return client.transactWriteItemsWithResponse(request)
  }

  override fun transactWriteItemsWithResponse(
    requestConsumer: Consumer<TransactWriteItemsEnhancedRequest.Builder>?
  ): TransactWriteItemsEnhancedResponse {
    return client.transactWriteItemsWithResponse(requestConsumer)
  }
}
