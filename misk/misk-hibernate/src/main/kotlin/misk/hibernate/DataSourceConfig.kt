package misk.hibernate

import misk.config.Config
import misk.environment.Environment
import java.time.Duration

/** Defines a type of datasource */
enum class DataSourceType(
  val driverClassName: String,
  val hibernateDialect: String,
  val buildJdbcUrl: (DataSourceConfig, Environment) -> String
) {
  MYSQL(
      driverClassName = "com.mysql.jdbc.Driver",
      hibernateDialect = "org.hibernate.dialect.MySQL57Dialect",
      buildJdbcUrl = { config, env ->
        val port = config.port ?: 3306
        val host = config.host ?: "127.0.0.1"
        val database = config.database ?: ""
        val testing = env == Environment.TESTING || env == Environment.DEVELOPMENT
        val queryParams = if (testing) "?createDatabaseIfNotExist=true" else ""
        "jdbc:mysql://$host:$port/$database$queryParams"
      }
  ),
  HSQLDB(
      driverClassName = "org.hsqldb.jdbcDriver",
      hibernateDialect = "org.hibernate.dialect.H2Dialect",
      buildJdbcUrl = { config, _ ->
        "jdbc:hsqldb:mem:${config.database!!};sql.syntax_mys=true"
      }
  ),
  VITESS(
      // TODO: Switch back to mysql protocol when this issue is fixed: https://github.com/vitessio/vitess/issues/4100
      // Find the correct buildJdbcUrl and port in the git history
      driverClassName = "io.vitess.jdbc.VitessDriver",
      hibernateDialect = "org.hibernate.dialect.MySQL57Dialect",
      buildJdbcUrl = { config, _ ->
        val port = config.port ?: 27001
        val host = config.host ?: "127.0.0.1"
        val database = config.database ?: ""
        "jdbc:vitess://$host:$port/$database"
      }
  ),
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
  val migrations_resource: String?,
  val vitess_schema_dir: String?
)

/** Configuration element for a cluster of DataSources */
data class DataSourceClusterConfig(
  val writer: DataSourceConfig,
  val reader: DataSourceConfig?
)

/** Top-level configuration element for all datasource clusters */
class DataSourceClustersConfig : LinkedHashMap<String, DataSourceClusterConfig>, Config {
  constructor() : super()
  constructor(m: Map<String, DataSourceClusterConfig>) : super(m)
}
