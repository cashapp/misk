package misk.jdbc

/**
 * Throws a [FullScatterException] for scatter queries that doesn't have a lookup vindex
 * and a [CowriteException] for queries that write to more than one shard.
 * Note: Current implementation is not thread safe and will not work in production.
 */
interface ScalabilityChecks: DataSourceDecorator {
}