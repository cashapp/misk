package misk.hibernate

/**
 * Marker interface for persistent entities. Ensures that only persistent entities can be passed
 * into [Session] methods.
 */
interface DbChild<R : DbRoot<R>, T : DbChild<R, T>> : DbMember<R, T> {
  override val id: Id<T>
    get() = cid.id
}