package misk.ratelimiting.bucket4j.dynamodb.v1.transaction

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue

internal class LongDynamoDBTransaction(private val key: Long, dynamoDb: AmazonDynamoDB, table: String) :
  BaseDynamoDBTransaction(dynamoDb, table) {
  override fun getKeyAttributeValue(): AttributeValue = AttributeValue().withN(key.toString())
}
