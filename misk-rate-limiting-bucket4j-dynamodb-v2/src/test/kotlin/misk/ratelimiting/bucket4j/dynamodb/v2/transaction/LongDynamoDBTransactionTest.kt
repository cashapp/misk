package misk.ratelimiting.bucket4j.dynamodb.v2.transaction

import com.google.inject.Module
import jakarta.inject.Inject
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbLongTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbLongTestModule.Companion.LONG_TABLE_NAME
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import kotlin.random.Random

@MiskTest(startService = true)
class LongDynamoDBTransactionTest : BaseDynamoDBTransactionTest<Long>() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = DynamoDbLongTestModule()

  @Inject private lateinit var dynamoDb: DynamoDbClient

  override fun createTransaction(key: Long): BaseDynamoDBTransaction =
    LongDynamoDBTransaction(key, dynamoDb, LONG_TABLE_NAME)

  override fun createRandomKey() = Random.nextLong()

  override fun keyToAttributeValue(key: Long): AttributeValue =
    AttributeValue.fromN(key.toString())
}
