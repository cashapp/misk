package misk.hibernate

import java.io.Serializable

/** Type-safe persistent identifier, mapped to a long column. */
data class Id<T : DbEntity<T>>(val id: Long) : Serializable {
  override fun toString() = id.toString()
}