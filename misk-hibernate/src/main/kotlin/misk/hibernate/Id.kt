package misk.hibernate

import misk.vitess.Shard
import java.io.Serializable

/** Type-safe persistent identifier, mapped to a long column. */
data class Id<T : DbEntity<T>>(val id: Long) : Serializable, Comparable<Id<T>> {
  override fun toString() = id.toString()

  override fun compareTo(other: Id<T>): Int {
    return id.compareTo(other.id)
  }

  fun shardKey() = Shard.Key.hash(id)
}
