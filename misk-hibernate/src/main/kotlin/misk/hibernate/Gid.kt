package misk.hibernate

import java.io.Serializable
import javax.persistence.Embeddable

/** Entity group id, maps to two long columns one for the root id and one for the row */
// experiment: this is also available for the root entity where the rootId and id is the
// same, let's see how that plays out
@Embeddable
data class Gid<R : DbRoot<R>, T : DbSharded<R, T>>(
  val rootId: Id<R>,
  val id: Id<T>
) : Serializable {
  override fun toString() = rootId.toString() + "/" + id.toString()

  fun shardKey() = rootId.shardKey()
}
