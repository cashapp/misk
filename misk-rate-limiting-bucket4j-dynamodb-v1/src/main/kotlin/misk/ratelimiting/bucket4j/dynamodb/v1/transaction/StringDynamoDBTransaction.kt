package misk.ratelimiting.bucket4j.dynamodb.v1.transaction

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue

internal class StringDynamoDBTransaction(private val key: String, dynamoDb: AmazonDynamoDB, table: String) :
  BaseDynamoDBTransaction(dynamoDb, table) {
  init {
    require(key.isNotEmpty()) { "Key must not be empty" }
    val keyBytes = key.toByteArray(Charsets.UTF_8).size
    require(keyBytes <= MAX_KEY_BYTES) {
      "Key $key has a length of $keyBytes bytes as a UTF-8 string, but the max allowed is $MAX_KEY_BYTES bytes"
    }
  }

  override fun getKeyAttributeValue(): AttributeValue = AttributeValue().withS(key)

  companion object {
    /**
     * Maximum DynamoDB string primary key size in bytes.
     *
     * [See
     * documentation](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.NamingRulesDataTypes.html#HowItWorks.DataTypes)
     */
    private const val MAX_KEY_BYTES = 2048
  }
}
