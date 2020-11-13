package misk.hibernate

import com.google.common.base.Strings
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Range
import okio.ByteString
import okio.ByteString.Companion.decodeHex

data class Shard(val keyspace: Keyspace, val name: String) {
  init {
    checkValidShardIdentifier(name)
  }

  override fun toString() = "${keyspace.name}/$name"

  // Don't serialize a Range via Gson.
  @Transient private val keyRange = Suppliers.memoize { this.toKeyRange() }

  fun keyRange(): Range<Key> {
    return keyRange.get()
  }

  private fun toKeyRange(): Range<Key> {
    if (name.equals(SINGLE_SHARD_ID)) {
      return Range.all<Key>()
    }
    val (lower, upper) = name.split("-", limit = 2)

    if (lower.isEmpty() && upper.isEmpty()) {
      return Range.all<Key>()
    }
    if (lower.isEmpty()) {
      return Range.lessThan(Key(upper))
    }
    if (upper.isEmpty()) {
      return Range.atLeast(Key(lower))
    }
    return Range.closedOpen(Key(lower), Key(upper))
  }

  operator fun contains(keyspaceId: Key): Boolean {
    return keyRange().contains(keyspaceId)
  }

  data class Key(val bytes: ByteString) : Comparable<Key> {
    init {
      check(bytes.size <= MAX_LENGTH) {
        "${bytes.hex()} is longer than the supported max length $MAX_LENGTH"
      }
    }

    override fun compareTo(other: Key): Int = bytes.compareTo(other.bytes)

    /**
     * Vitess always converts sharding keys to a left-justified binary string for computing a shard.
     * This left-justification makes the right-most zeroes insignificant and optional.
     *
     * To make compareTo, equals and hashCode work properly in Java, these keys are force padded
     * with 0's at the end. Dynamic end padding when comparing would break the equals/hashCode
     * contract in Java.
     */
    constructor(hex: String) : this(Strings.padEnd(hex, MAX_LENGTH, '0').decodeHex())

    companion object {
      fun hash(id: Long): Key {
        return Key(VitessHash.toKeyspaceId(id))
      }
    }
  }

  companion object {
    const val SINGLE_SHARD_ID = "0"
    const val MAX_LENGTH = 8
    val SINGLE_KEYSPACE = Keyspace("keyspace")
    val SINGLE_SHARD = Shard(SINGLE_KEYSPACE, SINGLE_SHARD_ID)
    val SINGLE_SHARD_SET = ImmutableSet.of(SINGLE_SHARD)

    fun parse(string: String): Shard {
      val (keyspace, shard) = string.split('/', ':', limit = 2)
      return Shard(Keyspace(keyspace), shard)
    }
  }
}
