package misk.mcp.internal

import de.justjanne.bitflags.Flag
import de.justjanne.bitflags.Flags
import de.justjanne.bitflags.of
import de.justjanne.bitflags.toBits
import de.justjanne.bitflags.toEnumSet
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import okio.Buffer
import okio.ByteString
import java.security.SecureRandom
import java.util.EnumSet
import java.util.UUID

/**
 * Converts ClientCapabilities to a UUID with embedded capability flags.
 * Each call generates a unique UUID while preserving the capability information.
 *
 * **Intended Use**: SessionIds scoped to this MCP server only.
 *
 * **Uniqueness Guarantees**: These UUIDs are NOT universally unique in the strict sense.
 * The implementation embeds metadata (capability flags) which reduces entropy:
 * - Bytes 14-15: Fixed capability bitmask (16 bits)
 * - Bytes 0-5: Timestamp component (48 bits, millisecond precision)
 * - Remaining bytes: Random data (~56 bits)
 * - Total entropy: ~104 bits (vs 122 bits for standard UUID v4)
 *
 * **Collision Risk**: With ~104 bits of entropy:
 * - 50% collision probability after ~2^52 UUIDs (~4.5 × 10^15)
 * - For practical MCP server usage (thousands to millions of sessions), collision risk is negligible
 * - Risk increases if UUIDs are shared across multiple servers or stored in a global namespace
 */
internal fun ClientCapabilities.toUuid(): UUID =
  fromBitmask(this.toBits())

/**
 * Extracts ClientCapabilities from a UUID created by [toUuid].
 */
internal fun ClientCapabilities.Companion.fromUuid(uuid:UUID): ClientCapabilities  =
  ClientCapabilities.fromBits(toBitmask(uuid))

/**
 * Creates a random UUID v8 with the capability bitmask embedded in bytes 14-15
 * and a timestamp component in bytes 0-5.
 *
 * UUID Structure (16 bytes):
 * - Bytes 0-5: Timestamp (48 bits, milliseconds since epoch)
 * - Byte 6: Version bits (upper nibble = 8) + random (lower nibble)
 * - Byte 7: Random
 * - Byte 8: Variant bits (upper 2 bits = 10) + random (lower 6 bits)
 * - Bytes 9-13: Random (40 bits)
 * - Bytes 14-15: Capability bitmask (16 bits)
 */
internal fun fromBitmask(mask: Long): UUID {
  val random = SecureRandom()
  val bytes = ByteArray(16)
  
  // Bytes 0-5: Timestamp component (48 bits, milliseconds since epoch)
  val timestamp = System.currentTimeMillis()
  bytes[0] = ((timestamp shr 40) and 0xFF).toByte()
  bytes[1] = ((timestamp shr 32) and 0xFF).toByte()
  bytes[2] = ((timestamp shr 24) and 0xFF).toByte()
  bytes[3] = ((timestamp shr 16) and 0xFF).toByte()
  bytes[4] = ((timestamp shr 8) and 0xFF).toByte()
  bytes[5] = (timestamp and 0xFF).toByte()
  
  // Bytes 6-13: Random data (with version/variant bits)
  val randomBytes = ByteArray(8)
  random.nextBytes(randomBytes)
  System.arraycopy(randomBytes, 0, bytes, 6, 8)

  // Store the capability bitmask in bytes 14-15 (16 bits for 16 flags)
  // Byte 14 gets the upper 8 bits, byte 15 gets the lower 8 bits
  bytes[14] = ((mask shr 8) and 0xFF).toByte()
  bytes[15] = (mask and 0xFF).toByte()

  // Set version (v8 → bits 76..79 = byte 6, upper nibble) and variant (RFC 4122 → bits 64..65 = byte 8, upper 2 bits)
  bytes[6] = (bytes[6].toInt() and 0x0F or (8 shl 4)).toByte()
  bytes[8] = (bytes[8].toInt() and 0x3F or 0x80).toByte()

  val bs = ByteString.of(*bytes)
  val high = bs.substring(0, 8).readLong()
  val low = bs.substring(8, 16).readLong()
  return UUID(high, low)
}

/**
 * Extracts the capability bitmask from bytes 14-15 of a UUID.
 */
internal fun toBitmask(uuid: UUID): Long {
  val buffer = Buffer()
  buffer.writeLong(uuid.mostSignificantBits)
  buffer.writeLong(uuid.leastSignificantBits)
  val bytes = buffer.readByteArray(16)

  // Extract the capability bitmask from bytes 14-15 (16 bits)
  // Byte 14 contains the upper 8 bits, byte 15 contains the lower 8 bits
  val upperByte = (bytes[14].toLong() and 0xFF) shl 8
  val lowerByte = bytes[15].toLong() and 0xFF
  return upperByte or lowerByte
}

/**
 * Reads 8 bytes from this ByteString as a Long in big-endian order.
 */
internal fun ByteString.readLong(): Long {
  require(size >= 8) { "ByteString must be at least 8 bytes" }
  var value = 0L
  for (i in 0 until 8) {
    value = (value shl 8) or (this[i].toLong() and 0xFF)
  }
  return value
}

/**
 * Bit flags for ClientCapabilities fields.
 */
internal enum class ClientCapabilitiesFlag(
  override val value: Long,
) : Flag<Long> {
  Sampling(0x0001L),
  Roots(0x0002L),
  RootsListChanged(0x0004L),
  Elicitation(0x0008L),
  Reserved5(0x0010L),
  Reserved6(0x0020L),
  Reserved7(0x0040L),
  Reserved8(0x0080L),
  Reserved9(0x0100L),
  Reserved10(0x0200L),
  Reserved11(0x0400L),
  Reserved12(0x0800L),
  Reserved13(0x1000L),
  Reserved14(0x2000L),
  Reserved15(0x4000L),
  Reserved16(0x8000L);

  companion object : Flags<Long, ClientCapabilitiesFlag> {
    override val all: Set<ClientCapabilitiesFlag> = entries.toEnumSet()
  }
}

/**
 * Converts ClientCapabilities to a bitmask.
 */
internal fun ClientCapabilities.toBits(): Long =
  toEnumSet().toBits()

/**
 * Converts a bitmask to ClientCapabilities.
 */
internal fun ClientCapabilities.Companion.fromBits(bits: Long): ClientCapabilities =
  ClientCapabilitiesFlag.of(bits).toClientCapabilities()

/**
 * Converts ClientCapabilities to an EnumSet of flags.
 */
internal fun ClientCapabilities.toEnumSet(): EnumSet<ClientCapabilitiesFlag> =
  buildList {
    sampling?.apply {
      add(ClientCapabilitiesFlag.Sampling)
    }
    roots?.apply {
      add(ClientCapabilitiesFlag.Roots)
      if (listChanged == true) {
        add(ClientCapabilitiesFlag.RootsListChanged)
      }
    }
    elicitation?.apply {
      add(ClientCapabilitiesFlag.Elicitation)
    }
  }.toEnumSet()

/**
 * Converts an EnumSet of flags to ClientCapabilities.
 */
internal fun EnumSet<ClientCapabilitiesFlag>.toClientCapabilities(): ClientCapabilities =
  ClientCapabilities(
    sampling = if (contains(ClientCapabilitiesFlag.Sampling)) EmptyJsonObject else null,
    roots = if (contains(ClientCapabilitiesFlag.Roots)) {
      ClientCapabilities.Roots(
        listChanged = if (contains(ClientCapabilitiesFlag.RootsListChanged)) true else null
      )
    } else null,
    elicitation = if (contains(ClientCapabilitiesFlag.Elicitation)) EmptyJsonObject else null
  )
