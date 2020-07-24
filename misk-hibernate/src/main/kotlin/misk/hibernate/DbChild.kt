package misk.hibernate

/**
 * Marker interface for sharded entities that stay with their root inside an entity group. Entity
 * group children will always stay inside the same shard regardless of shard splits and so on. That
 * means transactions inside an entity group is always safe. A typical sharding strategy has a
 * `DbCustomer` as a root and all of the entities that belong to that customer as children.
 */
interface DbChild<R : DbRoot<R>, T : DbChild<R, T>> : DbSharded<R, T> {
  override val id: Id<T>
    get() = gid.id
}
