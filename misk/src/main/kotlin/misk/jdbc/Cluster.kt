package misk.jdbc

/**
 * [Cluster] provides a consistent abstraction for
 * interacting with various database-related resources [T]
 * based on their role in the underlying database topology.
 *
 * Database clusters are often composed of a Read/Write master
 * instance and a set of Read-Only replica instances.
 *
 * Misk defers management of these replicas to the underlying
 * persistence layer and requires a single addressable endpoint.
 *
 * Examples include a Read-Only [DataSource] or a Read/Write
 * [SessionFactory] in Hibernate.
 *
 * N.B. If a Read-Only resource is not provided Misk will
 * default to the Read/Write resource.
 */
interface Cluster<T> {
  val writer: T
  val reader: T
}
