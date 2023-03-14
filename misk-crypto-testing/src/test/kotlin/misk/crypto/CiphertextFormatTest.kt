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
    private val fauxCiphertext = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
  }

  @Test
  fun testFromByteArrayV1() {
    val context = mapOf("key" to "value")
    val aad = "key=value".toByteArray(Charsets.UTF_8)
    val output = ByteArrayOutputStream()
    output.write(1) // VERSION
    output.writeBytes(byteArrayOf(0, 0, 0, 0)) // BITMASK
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
    output.writeBytes(bitmaskBytes) // BITMASK
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
    output.writeBytes(bitmaskBytes) // BITMASK
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
    output.writeBytes(byteArrayOf(0, 0, 0, 0)) // BITMASK
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
    output.writeBytes(byteArrayOf(0, 0, 0, 0)) // BITMASK
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
    output.writeBytes(bitmaskBytes) // BITMASK
    output.write(4) // CIPHERTEXT
    output.writeBytes(fauxCiphertext)

    val (ciphertext, map) = CiphertextFormat.deserializeFleFormat(output.toByteArray())
    assertThat(ciphertext).isEqualTo(fauxCiphertext)
    assertThat(map).containsKey("table_name")
  }

  @Test
  fun testInvalidPacketWithNoCiphertext() {
    val output =
      byteArrayOf(1, 0, 0, 0, 0, 5) // represents a V1 encryption packet with an invalid type
    assertThatThrownBy { CiphertextFormat.deserializeFleFormat(output) }
      .hasMessage("no ciphertext found")
  }

  @Test
  fun testInvalidPacketWithInvalidBitmask() {
    val output =
      byteArrayOf(1, 0, 1, 0, 0) // represents a V1 packet with a bitmask > Short.MAX_VALUE
    assertThatThrownBy { CiphertextFormat.deserializeFleFormat(output) }
      .hasMessage("invalid bitmask")
  }
}
