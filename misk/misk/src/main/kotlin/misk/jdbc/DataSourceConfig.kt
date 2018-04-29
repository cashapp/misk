package misk.jdbc

import misk.config.Config
import java.time.Duration

/** Defines a type of datasource */
enum class DataSourceType(
  val driverClassName: String,
  val buildJdbcUrl: (DataSourceConfig) -> String
) {
  MYSQL("com.mysql.jdbc.Driver", { config ->
    val port = config.port ?: 3306
    val host = config.host ?: "127.0.0.1"
    val database = config.database ?: ""
    "jdbc:mysql://${host}:$port/$database"

  }),
  HSQLDB("org.hsqldb.jdbcDriver", { config ->
    "jdbc:hsqldb:mem:${config.database!!}"
  })
}

/** Configuration element for an individual datasource */
data class DataSourceConfig(
  val type: DataSourceType,
  val host: String? = null,
  val port: Int? = null,
  val database: String? = null,
  val username: String? = null,
  val password: String? = null,
  val connection_properties: Map<String, String> = mapOf(),
  val fixed_pool_size: Int = 10,
  val connection_timeout: Duration = Duration.ofSeconds(30),
  val connection_max_lifetime: Duration = Duration.ofMinutes(30),
  val read_only: Boolean = false
)

/** Top-level configuration element for all databases */
data class DataSourcesConfig(val databases: Map<String, DataSourceConfig>) : Config
