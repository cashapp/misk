package misk.hibernate

/**
 * Marker interface for sharded persistent entities. Do not subclass directly, instead subclass either [DbRoot] for
 * entity group roots or [DbChild] for entity group children.
 */
interface DbSharded<R : DbRoot<R>, T : DbSharded<R, T>> : DbEntity<T> {
  val rootId: Id<R>
  val gid: Gid<R, T>
}
