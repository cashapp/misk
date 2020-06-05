package misk.hibernate

/**
 * Marker interface for persistent entities. Ensures that only persistent entities can be passed
 * into [Session] methods.
 */
interface DbEntity<T : DbEntity<T>> {
  val id: Id<T>
}