package misk.jdbc

/**
 * Figures out what database name to use for a given config. Tests use this to pool many databases
 * for concurrent execution. In development, staging, and production the database never changes.
 */
interface DatabasePool {
  /** Finds a database to satisfy [config] and returns a new config that targets it. */
  fun takeDatabase(config: DataSourceConfig): DataSourceConfig

  /** Releases a config created by [takeDatabase]. */
  fun releaseDatabase(config: DataSourceConfig)
}

object RealDatabasePool : DatabasePool {
  override fun takeDatabase(config: DataSourceConfig) = config

  override fun releaseDatabase(config: DataSourceConfig) {
    // Do nothing.
  }
}
