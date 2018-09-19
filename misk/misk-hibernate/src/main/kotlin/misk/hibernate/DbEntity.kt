package misk.hibernate

/**
 * Marker interface for persistent entities. Ensures that only persistent entities can be passed
 * into [Session] methods.
 *
 * You can't implement this interface directly because you need to decide on the scaling strategy:
 *   * Use [DbSharded] subclasses [DbRoot] or [DbChild] if the entity will have unbounded growth
 *   with usage.
 *   * Use [DbUnsharded] if your entity has bounded growth like metadata, configuration, static
 *   data etc.
 */
interface DbEntity<T : DbEntity<T>> {
  val id: Id<T>
}