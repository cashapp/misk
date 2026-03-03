package misk.hibernate

/**
 * Marker interface for persistent entities that have a bounded growth and do not require sharding.
 */
interface DbUnsharded<T : DbUnsharded<T>> : DbEntity<T>
