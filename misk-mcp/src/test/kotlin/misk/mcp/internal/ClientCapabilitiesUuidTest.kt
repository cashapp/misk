package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class ClientCapabilitiesUuidTest {

  @Test
  fun `toUuid converts empty capabilities to valid UUID`() {
    val capabilities = ClientCapabilities(
      sampling = null,
      roots = null,
      elicitation = null
    )

    val uuid = capabilities.toUuid()

    assertNotNull(uuid)
    // Verify it's a valid UUID by checking version and variant
    val version = (uuid.mostSignificantBits shr 12) and 0x0F
    assertEquals(8, version) // Should be version 8
  }

  @Test
  fun `toUuid converts capabilities with sampling to UUID`() {
    val capabilities = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = null,
      elicitation = null
    )

    val uuid = capabilities.toUuid()

    assertNotNull(uuid)
    // The UUID should encode the sampling bit (0x01)
    val bitmask = toBitmask(uuid)
    assertEquals(0x01L, bitmask)
  }

  @Test
  fun `toUuid converts capabilities with roots to UUID`() {
    val capabilities = ClientCapabilities(
      sampling = null,
      roots = ClientCapabilities.Roots(listChanged = null),
      elicitation = null
    )

    val uuid = capabilities.toUuid()

    assertNotNull(uuid)
    // The UUID should encode the roots bit (0x02)
    val bitmask = toBitmask(uuid)
    assertEquals(0x02L, bitmask)
  }

  @Test
  fun `toUuid converts capabilities with roots and listChanged to UUID`() {
    val capabilities = ClientCapabilities(
      sampling = null,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = null
    )

    val uuid = capabilities.toUuid()

    assertNotNull(uuid)
    // The UUID should encode both roots (0x02) and rootsListChanged (0x04) bits
    val bitmask = toBitmask(uuid)
    assertEquals(0x06L, bitmask) // 0x02 | 0x04
  }

  @Test
  fun `toUuid converts capabilities with elicitation to UUID`() {
    val capabilities = ClientCapabilities(
      sampling = null,
      roots = null,
      elicitation = EmptyJsonObject
    )

    val uuid = capabilities.toUuid()

    assertNotNull(uuid)
    // The UUID should encode the elicitation bit (0x08)
    val bitmask = toBitmask(uuid)
    assertEquals(0x08L, bitmask)
  }

  @Test
  fun `toUuid converts capabilities with all features to UUID`() {
    val capabilities = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )

    val uuid = capabilities.toUuid()

    assertNotNull(uuid)
    // The UUID should encode all bits: sampling (0x01) | roots (0x02) | rootsListChanged (0x04) | elicitation (0x08)
    val bitmask = toBitmask(uuid)
    assertEquals(0x0FL, bitmask) // 0x01 | 0x02 | 0x04 | 0x08
  }

  @Test
  fun `fromUuid converts UUID with empty capabilities back to ClientCapabilities`() {
    val originalCapabilities = ClientCapabilities(
      sampling = null,
      roots = null,
      elicitation = null
    )

    val uuid = originalCapabilities.toUuid()
    val restoredCapabilities = ClientCapabilities.fromUuid(uuid)

    assertEquals(originalCapabilities, restoredCapabilities)
  }

  @Test
  fun `fromUuid converts UUID with sampling back to ClientCapabilities`() {
    val originalCapabilities = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = null,
      elicitation = null
    )

    val uuid = originalCapabilities.toUuid()
    val restoredCapabilities = ClientCapabilities.fromUuid(uuid)

    assertEquals(originalCapabilities, restoredCapabilities)
  }

  @Test
  fun `fromUuid converts UUID with roots back to ClientCapabilities`() {
    val originalCapabilities = ClientCapabilities(
      sampling = null,
      roots = ClientCapabilities.Roots(listChanged = null),
      elicitation = null
    )

    val uuid = originalCapabilities.toUuid()
    val restoredCapabilities = ClientCapabilities.fromUuid(uuid)

    assertEquals(originalCapabilities, restoredCapabilities)
  }

  @Test
  fun `fromUuid converts UUID with roots and listChanged back to ClientCapabilities`() {
    val originalCapabilities = ClientCapabilities(
      sampling = null,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = null
    )

    val uuid = originalCapabilities.toUuid()
    val restoredCapabilities = ClientCapabilities.fromUuid(uuid)

    assertEquals(originalCapabilities, restoredCapabilities)
  }

  @Test
  fun `fromUuid converts UUID with elicitation back to ClientCapabilities`() {
    val originalCapabilities = ClientCapabilities(
      sampling = null,
      roots = null,
      elicitation = EmptyJsonObject
    )

    val uuid = originalCapabilities.toUuid()
    val restoredCapabilities = ClientCapabilities.fromUuid(uuid)

    assertEquals(originalCapabilities, restoredCapabilities)
  }

  @Test
  fun `fromUuid converts UUID with all features back to ClientCapabilities`() {
    val originalCapabilities = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )

    val uuid = originalCapabilities.toUuid()
    val restoredCapabilities = ClientCapabilities.fromUuid(uuid)

    assertEquals(originalCapabilities, restoredCapabilities)
  }

  @Test
  fun `toUuid and fromUuid round trip preserves empty capabilities`() {
    val original = ClientCapabilities(
      sampling = null,
      roots = null,
      elicitation = null
    )

    val uuid = original.toUuid()
    val restored = ClientCapabilities.fromUuid(uuid)

    assertEquals(original, restored)
  }

  @Test
  fun `toUuid and fromUuid round trip preserves sampling capability`() {
    val original = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = null,
      elicitation = null
    )

    val uuid = original.toUuid()
    val restored = ClientCapabilities.fromUuid(uuid)

    assertEquals(original, restored)
  }

  @Test
  fun `toUuid and fromUuid round trip preserves roots capability`() {
    val original = ClientCapabilities(
      sampling = null,
      roots = ClientCapabilities.Roots(listChanged = null),
      elicitation = null
    )

    val uuid = original.toUuid()
    val restored = ClientCapabilities.fromUuid(uuid)

    assertEquals(original, restored)
  }

  @Test
  fun `toUuid and fromUuid round trip preserves roots with listChanged capability`() {
    val original = ClientCapabilities(
      sampling = null,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = null
    )

    val uuid = original.toUuid()
    val restored = ClientCapabilities.fromUuid(uuid)

    assertEquals(original, restored)
  }

  @Test
  fun `toUuid and fromUuid round trip preserves all capabilities`() {
    val original = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )

    val uuid = original.toUuid()
    val restored = ClientCapabilities.fromUuid(uuid)

    assertEquals(original, restored)
  }

  @Test
  fun `fromUuid with manually created UUID decodes correctly`() {
    // Create a UUID with a specific bitmask (0x03 = sampling + roots)
    val uuid = fromBitmask(0x03L)
    val capabilities = ClientCapabilities.fromUuid(uuid)

    val expected = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = null),
      elicitation = null
    )
    assertEquals(expected, capabilities)
  }

  @Test
  fun `fromUuid with manually created UUID for all flags decodes correctly`() {
    // Create a UUID with all flags set (0x0F)
    val uuid = fromBitmask(0x0FL)
    val capabilities = ClientCapabilities.fromUuid(uuid)

    val expected = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )
    assertEquals(expected, capabilities)
  }

  @Test
  fun `UUID version and variant are correctly set`() {
    val capabilities = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = null,
      elicitation = null
    )

    val uuid = capabilities.toUuid()

    // Extract version (bits 76-79 of the UUID, which is bits 12-15 of mostSignificantBits)
    val version = (uuid.mostSignificantBits shr 12) and 0x0F
    assertEquals(8, version, "UUID should be version 8")

    // Extract variant (bits 64-65 of the UUID, which is bits 62-63 of leastSignificantBits)
    val variantBits = (uuid.leastSignificantBits ushr 62) and 0x03
    assertEquals(2, variantBits, "UUID should have RFC 4122 variant (10 in binary)")
  }

  @Test
  fun `different capability combinations produce different UUIDs`() {
    val caps1 = ClientCapabilities(sampling = EmptyJsonObject, roots = null, elicitation = null)
    val caps2 = ClientCapabilities(sampling = null, roots = ClientCapabilities.Roots(null), elicitation = null)
    val caps3 = ClientCapabilities(sampling = null, roots = null, elicitation = EmptyJsonObject)

    val uuid1 = caps1.toUuid()
    val uuid2 = caps2.toUuid()
    val uuid3 = caps3.toUuid()

    // All UUIDs should be different
    assert(uuid1 != uuid2) { "UUID for sampling should differ from UUID for roots" }
    assert(uuid1 != uuid3) { "UUID for sampling should differ from UUID for elicitation" }
    assert(uuid2 != uuid3) { "UUID for roots should differ from UUID for elicitation" }
  }

  @Test
  fun `same capability combinations produce different UUIDs due to randomness`() {
    val caps1 = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )
    val caps2 = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )

    val uuid1 = caps1.toUuid()
    val uuid2 = caps2.toUuid()

    // UUIDs should be different due to random component
    assert(uuid1 != uuid2) { "Same capabilities should produce different UUIDs due to randomness" }
    
    // But both should decode to the same capabilities
    val decoded1 = ClientCapabilities.fromUuid(uuid1)
    val decoded2 = ClientCapabilities.fromUuid(uuid2)
    
    assertEquals(caps1, decoded1)
    assertEquals(caps2, decoded2)
  }

  @Test
  fun `multiple UUIDs from same capabilities all decode correctly`() {
    val capabilities = ClientCapabilities(
      sampling = EmptyJsonObject,
      roots = ClientCapabilities.Roots(listChanged = true),
      elicitation = EmptyJsonObject
    )

    // Generate multiple UUIDs from the same capabilities
    val uuids = List(10) { capabilities.toUuid() }

    // All UUIDs should be unique
    assertEquals(10, uuids.toSet().size, "All generated UUIDs should be unique")

    // But all should decode to the same capabilities
    uuids.forEach { uuid ->
      val decoded = ClientCapabilities.fromUuid(uuid)
      assertEquals(capabilities, decoded)
    }
  }
}
