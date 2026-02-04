package misk.ratelimiting.bucket4j.dynamodb.v2.transaction

import jakarta.inject.Inject
import java.util.Optional
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbLongTestModule.Companion.LONG_TABLE_NAME
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule.Companion.STRING_TABLE_NAME
import misk.ratelimiting.bucket4j.dynamodb.v2.transaction.BaseDynamoDBTransaction.Companion.DEFAULT_KEY_NAME
import misk.ratelimiting.bucket4j.dynamodb.v2.transaction.BaseDynamoDBTransaction.Companion.DEFAULT_STATE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

abstract class BaseDynamoDBTransactionTest<K> {
  @Inject private lateinit var dynamoDb: DynamoDbClient

  @Test
  fun `should return empty when no bucket exists`() {
    val transaction = createTransaction(createRandomKey())
    assertThat(transaction.getStateData(Optional.empty<Long>()).isEmpty).isTrue()
  }

  @Test
  fun `should return empty when bucket exists but state is null`() {
    val key = createRandomKey()

    saveState(key, null)

    val transaction = createTransaction(key)
    assertThat(transaction.getStateData(Optional.empty<Long>()).isEmpty).isTrue()
  }

  @Test
  fun `should throw when bucket state is not binary`() {
    val key = createRandomKey()
    val state = AttributeValue.fromS("invalid bucket state")

    saveState(key, state)

    val transaction = createTransaction(key)
    assertThrows<IllegalStateException> { transaction.getStateData(Optional.empty<Long>()) }
  }

  @Test
  fun `should return current state when binary`() {
    val key = createRandomKey()
    val state = AttributeValue.fromB(SdkBytes.fromUtf8String("state"))

    saveState(key, state)

    val transaction = createTransaction(key)
    assertThat(transaction.getStateData(Optional.empty<Long>()).isPresent).isTrue()
    assertThat(transaction.getStateData(Optional.empty<Long>()).get()).isEqualTo(state.b().asByteArray())
  }

  @Test
  fun `should swap when original data is null`() {
    val key = createRandomKey()
    val update = AttributeValue.fromB(SdkBytes.fromUtf8String("update"))
    val transaction = createTransaction(key)

    val result = transaction.compareAndSwap(null, update.b().asByteArray(), null, Optional.empty<Long>())
    val state = getState(key)

    assertThat(result).isTrue()
    assertThat(state.b().asByteArray()).isEqualTo(update.b().asByteArray())
  }

  @Test
  fun `should swap when original data equals updated data`() {
    val key = createRandomKey()
    val original = AttributeValue.fromB(SdkBytes.fromUtf8String("update"))
    val update = AttributeValue.fromB(SdkBytes.fromUtf8String("update"))
    val transaction = createTransaction(key)

    val result =
      transaction.compareAndSwap(original.b().asByteArray(), update.b().asByteArray(), null, Optional.empty<Long>())
    val state = getState(key)

    assertThat(result).isTrue()
    assertThat(state.b().asByteArray()).isEqualTo(update.b().asByteArray())
  }

  @Test
  fun `should not swap when original data does not equal updated data`() {
    val key = createRandomKey()
    val initial = AttributeValue.fromB(SdkBytes.fromUtf8String("initial"))
    val original = AttributeValue.fromB(SdkBytes.fromUtf8String("original"))
    val update = AttributeValue.fromB(SdkBytes.fromUtf8String("update"))

    saveState(key, initial)
    val transaction = createTransaction(key)

    val result =
      transaction.compareAndSwap(original.b().asByteArray(), update.b().asByteArray(), null, Optional.empty<Long>())
    val state = getState(key)

    assertThat(result).isFalse()
    assertThat(state.b().asByteArray()).isEqualTo(initial.b().asByteArray())
  }

  private fun saveState(key: K, state: AttributeValue?) {
    dynamoDb.putItem {
      it.tableName(getTableName(key))
      it.item(mapOf(DEFAULT_KEY_NAME to keyToAttributeValue(key), DEFAULT_STATE_NAME to state))
    }
  }

  private fun getState(key: K): AttributeValue =
    dynamoDb
      .getItem {
        it.tableName(getTableName(key))
        it.consistentRead(true)
        it.key(mapOf(DEFAULT_KEY_NAME to keyToAttributeValue(key)))
      }
      .item()
      .getValue(DEFAULT_STATE_NAME)

  private fun getTableName(key: K): String {
    return when (key) {
      is Long -> LONG_TABLE_NAME
      is String -> STRING_TABLE_NAME
      else -> throw IllegalArgumentException("Unsupported key type: ${key!!.javaClass.name}")
    }
  }

  internal abstract fun createTransaction(key: K): BaseDynamoDBTransaction

  internal abstract fun createRandomKey(): K

  internal abstract fun keyToAttributeValue(key: K): AttributeValue
}
