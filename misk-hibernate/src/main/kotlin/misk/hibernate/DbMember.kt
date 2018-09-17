package misk.hibernate

/**
 * Marker interface for persistent entities. Ensures that only persistent entities can be passed
 * into [Session] methods.
 */
interface DbMember<R : DbRoot<R>, T : DbMember<R, T>> : DbEntity<T> {
  val rootId : Id<R>
  val cid : Cid<R, T>
}