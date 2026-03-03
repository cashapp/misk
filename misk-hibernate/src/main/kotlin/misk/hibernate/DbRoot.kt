package misk.hibernate

/**
 * Marker interface for sharded entity group roots. Entity group roots are spread out across shards
 * they can also have children in the form of [DbChild] subclasses that always stay in the same
 * shard as their roots. A typical root is for example `DbCustomer`.
 */
interface DbRoot<T : DbRoot<T>> : DbSharded<T, T> {
  override val gid: Gid<T, T>
    get() = Gid(id, id)

  override val rootId: Id<T>
    get() = id
}
