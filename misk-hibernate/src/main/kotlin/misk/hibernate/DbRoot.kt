package misk.hibernate

/**
 * Marker interface for persistent entities. Ensures that only persistent entities can be passed
 * into [Session] methods.
 */
interface DbRoot<T : DbRoot<T>> : DbMember<T, T> {
  override val cid: Cid<T, T>
    get() = Cid(id, id)
}