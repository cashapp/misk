package misk.crypto

import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

@MiskTest
class CiphertextFormatTest {

  companion object {
    private const val VERSION_INDEX = 0
    private const val EC_LENGTH_INDEX = 1
    private val fauxCiphertext = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
  }

  @Test
  fun testBasicEncryptionContextSerialization() {
    val context = mapOf(
        "table_name" to "unimportant",
        "database_name" to "unimportant",
        "key" to "value")
    val serialized = CiphertextFormat.serializeEncryptionContext(context)
    assertThat(CiphertextFormat.deserializeEncryptionContext(serialized))
        .isNotNull
        .isEqualTo(context)
  }

  @Test
  fun testEncryptionContextSerializationSortsKeys() {
    val context = mapOf(
        "table_name" to "unimportant",
        "database_name" to "unimportant",
        "key" to "value")
    val context2 = linkedMapOf(
        "key" to "value",
        "database_name" to "unimportant",
        "table_name" to "unimportant")

    assertThat(CiphertextFormat.serializeEncryptionContext(context))
        .isEqualTo(CiphertextFormat.serializeEncryptionContext(context2))
  }

  @Test
  fun testEncryptionContextSerializationWithVarInts() {
    val context = mutableMapOf<String, String>()
    (0..300).forEach { context["$it"] = UUID.randomUUID().toString() }
    val serialized = CiphertextFormat.serializeEncryptionContext(context)
    assertThat(CiphertextFormat.deserializeEncryptionContext(serialized))
        .isNotNull
        .isEqualTo(context)
  }

  @Test
  fun testEmptyEncryptionContext() {
    assertThat(CiphertextFormat.serializeEncryptionContext(mapOf())).isNull()
  }

  @Test
  fun testNullEncryptionContext() {
    assertThat(CiphertextFormat.serializeEncryptionContext(null)).isNull()
  }

  @Test
  fun testEncryptionContextValueTooLong() {
    val value = (0..Short.MAX_VALUE).joinToString("") { "a" }
    val context = mapOf("key" to value)
    assertThatThrownBy { CiphertextFormat.serializeEncryptionContext(context) }
        .hasMessage("value is too long")
  }

  @Test
  fun testEncryptionContextKeyTooLong() {
    val key = (0..Short.MAX_VALUE).joinToString("") { "a" }
    val context = mapOf(key to "value")
    assertThatThrownBy { CiphertextFormat.serializeEncryptionContext(context) }
        .hasMessage("key is too long")
  }

  @Test
  fun testEncryptionContextTooLong() {
    val key = (100..Short.MAX_VALUE).joinToString("") { "a" }
    val value = (100..Short.MAX_VALUE).joinToString("") { "a" }
    assertThatThrownBy { CiphertextFormat.serializeEncryptionContext(mapOf(key to value)) }
        .hasMessage("encryption context is too long")
  }

  @Test
  fun testEncryptionContextWithForbiddenCharacters() {
    var context = mapOf("" to "value")
    assertThatThrownBy { CiphertextFormat.serializeEncryptionContext(context) }
        .hasMessage("empty key or value")
    context = mapOf("key" to "")
    assertThatThrownBy { CiphertextFormat.serializeEncryptionContext(context) }
        .hasMessage("empty key or value")
  }

  @Test
  fun testFromByteArrayWithNoContext() {
    val aad = CiphertextFormat.serializeEncryptionContext(null)
    val serialized = CiphertextFormat.serialize(fauxCiphertext, aad)
    assertThatCode { CiphertextFormat.deserialize(serialized, null) }
        .doesNotThrowAnyException()
    assertThatCode { CiphertextFormat.deserialize(serialized, mapOf()) }
        .doesNotThrowAnyException()
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, mapOf("key" to "value")) }
        .isInstanceOf(CiphertextFormat.UnexpectedEncryptionContextException::class.java)
  }

  @Test
  fun testFromByteArrayWithContext() {
    val context = mapOf("key" to "value")
    val aad = CiphertextFormat.serializeEncryptionContext(context)
    val serialized = CiphertextFormat.serialize(fauxCiphertext, aad)
    assertThatCode { CiphertextFormat.deserialize(serialized, context) }
        .doesNotThrowAnyException()
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, mapOf("wrong_key" to "wrong_value")) }
        .isInstanceOf(CiphertextFormat.EncryptionContextMismatchException::class.java)
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, null) }
        .isInstanceOf(CiphertextFormat.MissingEncryptionContextException::class.java)
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, emptyMap()) }
        .isInstanceOf(CiphertextFormat.MissingEncryptionContextException::class.java)
  }

  @Test
  fun testFromByteArrayWithLongContext() {
    val context = mutableMapOf<String, String>()
    (0..300).forEach { context["$it"] = UUID.randomUUID().toString() }
    val aad = CiphertextFormat.serializeEncryptionContext(context)
    val serialized = CiphertextFormat.serialize(fauxCiphertext, aad)
    val (ciphertext, ciphertextAad) = CiphertextFormat.deserialize(serialized, context)
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(ciphertextAad).isEqualTo(aad)
  }

  @Test
  fun testFromByteArrayWithEmptyContext() {
    val context = mapOf<String, String>()
    val aad = CiphertextFormat.serializeEncryptionContext(context)
    val serialized = CiphertextFormat.serialize(fauxCiphertext, aad)
    assertThatCode { CiphertextFormat.deserialize(serialized, context) }
        .doesNotThrowAnyException()
    assertThatCode { CiphertextFormat.deserialize(serialized, null) }
        .doesNotThrowAnyException()
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, mapOf("key" to "value")) }
        .isInstanceOf(CiphertextFormat.UnexpectedEncryptionContextException::class.java)
  }

  @Test
  fun testUnsupportedSchemaVersion() {
    val context = mapOf("key" to "value")
    val aad = CiphertextFormat.serializeEncryptionContext(context)
    val serialized = CiphertextFormat.serialize(fauxCiphertext, aad)
    serialized[VERSION_INDEX] = 3
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, context) }
        .hasMessage("invalid version: 3")
  }

  @Test
  fun testWrongEncryptionContextSize() {
    val context = mapOf("key" to "value", "key2" to "value2")
    val aad = CiphertextFormat.serializeEncryptionContext(context)
    val serialized = CiphertextFormat.serialize(fauxCiphertext, aad)
    serialized[EC_LENGTH_INDEX + 1] = 1
    assertThatThrownBy { CiphertextFormat.deserialize(serialized, context) }
        .hasMessage("encryption context doesn't match")
  }

  @Test
  fun testFromByteArrayV1() {
    val context = mapOf("key" to "value")
    val aad = "key=value".toByteArray(Charsets.UTF_8)
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    output.writeBytes(byteArrayOf(0, 0, 0, 0))  // BITMASK
    output.write(2) // ENCRYPTION_CONTEXT
    val ecLength = ByteBuffer.allocate(2)
        .putShort(aad.size.toShort())
        .array()
    output.writeBytes(ecLength) // EXPANDED_CONTEXT length
    output.writeBytes(aad)
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).containsExactly(context.entries.first())
  }

  @Test
  fun testFromByteArrayV1WithBitmask() {
    val aad = "key=value".toByteArray(Charsets.UTF_8)
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    val bitmask = 1 shl 1 // TABLE_NAME
    val bitmaskBytes = ByteBuffer.allocate(4)
        .putInt(bitmask)
        .array()
    output.writeBytes(bitmaskBytes)  // BITMASK
    output.write(1) // EXPANDED_CONTEXT_DESCRIPTION
    val ecLength = ByteBuffer.allocate(2)
        .putShort(aad.size.toShort())
        .array()
    output.writeBytes(ecLength) // EXPANDED_CONTEXT length
    output.writeBytes(aad)
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).containsKey("table_name")
  }

  @Test
  fun testFromByteArrayV1WithBitmaskAndFullContext() {
    val context = mapOf("key" to "value")
    val aad = "key=value".toByteArray(Charsets.UTF_8)
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    val bitmask = 1 shl 1 // TABLE_NAME
    val bitmaskBytes = ByteBuffer.allocate(4)
        .putInt(bitmask)
        .array()
    output.writeBytes(bitmaskBytes)  // BITMASK
    output.write(2) // ENCRYPTION_CONTEXT
    val ecLength = ByteBuffer.allocate(2)
        .putShort(aad.size.toShort())
        .array()
    output.writeBytes(ecLength) // EXPANDED_CONTEXT length
    output.writeBytes(aad)
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).containsExactly(context.entries.first())
  }

  @Test
  fun testFromByteArrayV1NoContext() {
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    output.writeBytes(byteArrayOf(0, 0, 0, 0))  // BITMASK
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).isEmpty()
  }

  @Test
  fun testFromByteArrayV1EmptyContext() {
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    output.writeBytes(byteArrayOf(0, 0, 0, 0))  // BITMASK
    output.write(2) // ENCRYPTION_CONTEXT
    output.writeBytes(byteArrayOf(0, 0))
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).isEmpty()
  }

  @Test
  fun testFromByteArrayV1BitmaskOnly() {
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    val bitmask = 1 shl 1 // TABLE_NAME
    val bitmaskBytes = ByteBuffer.allocate(4)
        .putInt(bitmask)
        .array()
    output.writeBytes(bitmaskBytes)  // BITMASK
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).containsKey("table_name")
  }

  @Test
  fun testInvalidPacketWithNoCiphertext() {
    val output = byteArrayOf(1, 0, 0, 0, 0, 5) // represents a V1 encryption packet with an invalid type
    assertThatThrownBy { CiphertextFormat.deserializeFleFormat(output) }
        .hasMessage("no ciphertext found")
  }

  @Test
  fun testInvalidPacketWithInvalidBitmask() {
    val output = byteArrayOf(1, 0, 1, 0, 0) // represents a V1 packet with a bitmask > Short.MAX_VALUE
    assertThatThrownBy { CiphertextFormat.deserializeFleFormat(output) }
        .hasMessage("invalid bitmask")
  }
}