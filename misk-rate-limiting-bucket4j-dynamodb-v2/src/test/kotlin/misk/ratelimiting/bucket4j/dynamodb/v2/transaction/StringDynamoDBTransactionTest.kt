package misk.ratelimiting.bucket4j.dynamodb.v2.transaction

import com.google.inject.Module
import jakarta.inject.Inject
import kotlin.random.Random
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule.Companion.STRING_TABLE_NAME
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@MiskTest(startService = true)
class StringDynamoDBTransactionTest : BaseDynamoDBTransactionTest<String>() {
  @Suppress("unused") @MiskTestModule private val module: Module = DynamoDbStringTestModule()

  @Inject private lateinit var dynamoDb: DynamoDbClient

  @Test
  fun `throw when key exceeds max length`() {
    assertThrows<IllegalArgumentException> { createTransaction("a".repeat(4000)) }
  }

  override fun createTransaction(key: String): BaseDynamoDBTransaction =
    StringDynamoDBTransaction(key, dynamoDb, STRING_TABLE_NAME)

  override fun createRandomKey() = Random.nextLong().toString()

  override fun keyToAttributeValue(key: String): AttributeValue = AttributeValue.fromS(key)
}
