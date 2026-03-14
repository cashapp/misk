package misk.jdbc

import javax.sql.DataSource

/**
 * Registers a [DataSource] for the admin database dashboard tab. [JdbcModule] automatically registers one for each
 * writer DataSource it binds.
 */
data class AdminDatabaseEntry(val name: String, val dataSource: DataSource)
