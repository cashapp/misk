package misk.ratelimiting.bucket4j.dynamodb.v2.transaction

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal class LongDynamoDBTransaction(
  private val key: Long,
  dynamoDb: DynamoDbClient,
  table: String
) : BaseDynamoDBTransaction(dynamoDb, table) {
  override fun getKeyAttributeValue(): AttributeValue = AttributeValue.fromN(key.toString())
}
