package misk.hibernate

import misk.vitess.Shard
import java.io.Serializable

/** Type-safe persistent identifier, mapped to a long column. */
data class Id<T : DbEntity<T>>(val id: Long) : Serializable {
  override fun toString() = id.toString()

  fun shardKey() = Shard.Key.hash(id)
}