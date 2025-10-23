package misk.jdbc

import misk.config.Redact
import misk.config.Config
import misk.containers.ContainerUtil
import wisp.deployment.Deployment
import java.time.Duration
import java.util.Properties

/** Defines a type of datasource */
enum class DataSourceType(
  val driverClassName: String,
  val hibernateDialect: String,
  val isVitess: Boolean
) {
  MYSQL(
    driverClassName = "io.opentracing.contrib.jdbc.TracingDriver",
    hibernateDialect = "org.hibernate.dialect.MySQL8Dialect",
    isVitess = false
  ),
  HSQLDB(
    driverClassName = "org.hsqldb.jdbcDriver",
    hibernateDialect = "org.hibernate.dialect.H2Dialect",
    isVitess = false
  ),
  VITESS_MYSQL(
    driverClassName = MYSQL.driverClassName,
    hibernateDialect = "misk.hibernate.vitess.VitessDialect",
    isVitess = true
  ),
  COCKROACHDB(
    driverClassName = "org.postgresql.Driver",
    hibernateDialect = "org.hibernate.dialect.PostgreSQL95Dialect",
    isVitess = false
  ),
  POSTGRESQL(
    driverClassName = "org.postgresql.Driver",
    hibernateDialect = "org.hibernate.dialect.PostgreSQL95Dialect",
    isVitess = false
  ),
  TIDB(
    driverClassName = "io.opentracing.contrib.jdbc.TracingDriver",
    hibernateDialect = "org.hibernate.dialect.MySQL8Dialect",
    isVitess = false
  ),
}

enum class MigrationsFormat {
  /**
   * Traditional migrations format where each migration file represents DB schema change
   */
  TRADITIONAL,
  /**
   * Declarative migrations format where each migration file represents a DB table
   */
  DECLARATIVE,
  /**
   * Externally managed migrations where schema migrations are handled outside of the application.
   * When this format is used, SchemaMigratorService will not be installed.
   */
  EXTERNALLY_MANAGED
}

/** Configuration element for an individual datasource */
data class DataSourceConfig @JvmOverloads constructor(
  val type: DataSourceType,
  val host: String? = null,
  val port: Int? = null,
  val database: String? = null,
  val username: String? = null,
  @Redact
  val password: String? = null,
  val fixed_pool_size: Int = 10,
  val connection_timeout: Duration = Duration.ofSeconds(10),
  val validation_timeout: Duration = Duration.ofSeconds(3),
  val connection_idle_timeout: Duration? = null,
  val connection_max_lifetime: Duration = Duration.ofMinutes(1),
  val query_timeout: Duration? = Duration.ofMinutes(1),
  val keepalive_time: Duration = Duration.ofSeconds(0),
  val migrations_resource: String? = null,
  val migrations_resources: List<String>? = null,
  /**
   * List of filenames to exclude from being processed in database schema migrations
   */
  val migrations_resources_exclusion: List<String>? = null,
  /**
   * Regular expression *traditional* migration files names should match.
   * Any migration filename that doesn't match the given regular expression will cause an exception,
   * unless it was explicitly mentioned in [migrations_resources_exclusion].
   * Declarative schema migrator will enforce the opposite - all migration files should not match the given regular expression.
   */
  val migrations_resources_regex: String = "(^|.*/)v(\\d+)__[^/]+\\.sql",
  val vitess_schema_resource_root: String? = null,
  /*
     See https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-using-ssl.html for
     trust_certificate_key_store_* details.
   */
  val trust_certificate_key_store_url: String? = null,
  @Redact
  val trust_certificate_key_store_password: String? = null,
  val client_certificate_key_store_url: String? = null,
  @Redact
  val client_certificate_key_store_password: String? = null,
  // Vitess driver doesn't support passing in URLs so support paths and prefer this for Vitess
  // going forward
  val trust_certificate_key_store_path: String? = null,
  val client_certificate_key_store_path: String? = null,
  val verify_server_identity: Boolean = false,
  val enabledTlsProtocols: List<String> = listOf("TLSv1.2", "TLSv1.3"),
  val show_sql: String? = "false",
  // Don't enable statistics unless we really need to.
  // See http://tech.puredanger.com/2009/05/13/hibernate-concurrency-bugs/
  // for an explanation of the drawbacks to Hibernate's StatisticsImpl.
  val generate_hibernate_stats: String? = "false",
  // Consider using this if you want Hibernate to automagically batch inserts/updates when it can.
  val jdbc_statement_batch_size: Int? = null,
  val use_fixed_pool_size: Boolean = false,
  // MySQL 8+ by default requires authentication via RSA keys if TLS is unavailable.
  // Within secured subnets, overriding to true can be acceptable.
  // See https://mysqlconnector.net/troubleshooting/retrieval-public-key/
  val allow_public_key_retrieval: Boolean = false,
  // Allow setting additional JDBC url parameters for advanced configuration
  val jdbc_url_query_parameters: Map<String, Any> = mapOf(),
  /*
    Implements a custom JDBC4 `Connection.isValid()` which validates that connections are writable.
    If a connection isn't writable, it will be evicted from the connection pool within at most
    the `validationTimeout`. This setting ensures that connections are rapidly evicted
    and reconnections are attempted if the application is connected to a read only DB instance
    when it expects a writable DB instance.

    Has no effect if set on a datasource that isn't DataSourceType.MYSQL.

    Full details: https://github.com/cashapp/misk/pull/3094#issue-2080624417
  */
  val mysql_enforce_writable_connections: Boolean = false,
  val migrations_format: MigrationsFormat = MigrationsFormat.TRADITIONAL,
  val declarative_schema_config: DeclarativeSchemaConfig? = null,
  val mysql_use_aws_secret_for_credentials: Boolean = false,
  val mysql_aws_secret_name: String? = null,
) {
  init {
    if (migrations_format == MigrationsFormat.DECLARATIVE) {
      require(type == DataSourceType.MYSQL) {
        "Declarative migrations are only supported for MySQL"
      }
    } else if (migrations_format == MigrationsFormat.TRADITIONAL) {
      require(declarative_schema_config == null) {
        "declarative_schmea_config.excluded_tables is only supported for declarative migrations"
      }
    }
  }
  fun getDriverClassName(): String {
    return if (mysql_use_aws_secret_for_credentials) {
      "com.amazonaws.secretsmanager.sql.AWSSecretsManagerMySQLDriver"
    } else {
      type.driverClassName
    }
  }

  fun getDataSourceProperties() : Properties {
    // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
    val properties = Properties()

    properties["cachePrepStmts"] = "true"
    properties["prepStmtCacheSize"] = "250"
    properties["prepStmtCacheSqlLimit"] = "2048"
    if (type == DataSourceType.MYSQL || type == DataSourceType.VITESS_MYSQL || type == DataSourceType.TIDB) {
      properties["useServerPrepStmts"] = "true"
    }
    if (mysql_use_aws_secret_for_credentials) {
      properties["user"] = mysql_aws_secret_name
    }
    properties["useLocalSessionState"] = "true"
    properties["rewriteBatchedStatements"] = "true"
    properties["cacheResultSetMetadata"] = "true"
    properties["cacheServerConfiguration"] = "true"
    properties["elideSetAutoCommits"] = "true"
    properties["maintainTimeStats"] = "false"
    properties["characterEncoding"] = "UTF-8"

    return properties
  }

  fun withDefaults(): DataSourceConfig {
    val server_hostname = ContainerUtil.dockerTargetOrLocalIp()
    return when (type) {
      DataSourceType.MYSQL -> {
        copy(
          port = port ?: 3306,
          host = host ?: System.getenv("MYSQL_HOST") ?: server_hostname,
          database = database ?: ""
        )
      }
      DataSourceType.TIDB -> {
        copy(
          port = port ?: 4000,
          host = host ?: server_hostname,
          database = database ?: ""
        )
      }
      DataSourceType.VITESS_MYSQL -> {
        copy(
          port = port ?: 27003,
          host = host ?: System.getenv("VITESS_HOST") ?: server_hostname,
          database = database ?: "@primary"
        )
      }
      DataSourceType.HSQLDB -> {
        this
      }
      DataSourceType.COCKROACHDB -> {
        copy(
          username = "root",
          port = port ?: 26257,
          host = host ?: server_hostname,
          database = database ?: ""
        )
      }
      DataSourceType.POSTGRESQL -> {
        copy(
          port = port ?: 5432,
          host = host ?: server_hostname,
          database = database ?: ""
        )
      }
    }
  }

  fun buildJdbcUrl(deployment: Deployment): String {
    val config = withDefaults()

    require(config.client_certificate_key_store_path.isNullOrBlank() || config.client_certificate_key_store_url.isNullOrBlank()) {
      "can optionally set client_certificate_key_store_path or client_certificate_key_store_url, but not both"
    }

    require(config.trust_certificate_key_store_path.isNullOrBlank() || config.trust_certificate_key_store_url.isNullOrBlank()) {
      "can optionally set trust_certificate_key_store_path or trust_certificate_key_store_url, but not both"
    }

    return when (type) {
      DataSourceType.MYSQL, DataSourceType.VITESS_MYSQL, DataSourceType.TIDB -> {
        var queryParams = "?useLegacyDatetimeCode=false"

        if (deployment.isTest || deployment.isLocalDevelopment) {
          queryParams += "&createDatabaseIfNotExist=true"
        }

        queryParams += "&connectTimeout=${config.connection_timeout.toMillis()}"

        if (config.query_timeout != null) {
          queryParams += "&socketTimeout=${config.query_timeout.toMillis()}"
        }

        if (type == DataSourceType.VITESS_MYSQL) {
          queryParams += "&useServerPrepStmts=true"
          // If we leave this as the default (true) the logs get spammed with the following errors:
          // "Ignored inapplicable SET {sql_mode } = strict_trans_tables"
          // Since Vitess always uses strict_trans_tables this makes no difference here except it
          // stops spamming the logs
          queryParams += "&jdbcCompliantTruncation=false"
        }

        val trustStoreUrl: String? =
          if (!config.trust_certificate_key_store_path.isNullOrBlank()) {
            "file://${config.trust_certificate_key_store_path}"
          } else if (!config.trust_certificate_key_store_url.isNullOrBlank()) {
            config.trust_certificate_key_store_url
          } else {
            null
          }
        val certStoreUrl =
          if (!config.client_certificate_key_store_path.isNullOrBlank()) {
            "file://${config.client_certificate_key_store_path}"
          } else if (!config.client_certificate_key_store_url.isNullOrBlank()) {
            config.client_certificate_key_store_url
          } else {
            null
          }

        var useSSL = false

        if (!trustStoreUrl.isNullOrBlank()) {
          require(!config.trust_certificate_key_store_password.isNullOrBlank()) {
            "must provide a trust_certificate_key_store_password"
          }
          queryParams += "&trustCertificateKeyStoreUrl=$trustStoreUrl"
          queryParams += "&trustCertificateKeyStorePassword=${config.trust_certificate_key_store_password}"
          useSSL = true
        }
        if (!certStoreUrl.isNullOrBlank()) {
          require(!config.client_certificate_key_store_password.isNullOrBlank()) {
            "must provide a client_certificate_key_store_password if client_certificate_key_store_url" +
              " or client_certificate_key_store_path is set"
          }
          queryParams += "&clientCertificateKeyStoreUrl=$certStoreUrl"
          queryParams += "&clientCertificateKeyStorePassword=${config.client_certificate_key_store_password}"
          useSSL = true
        }

        val sslMode = if (useSSL && verify_server_identity) {
          "VERIFY_IDENTITY"
        } else if (useSSL) {
          "VERIFY_CA"
        } else {
          "PREFERRED"
        }
        queryParams += "&sslMode=$sslMode"

        if (enabledTlsProtocols.isNotEmpty()) {
          queryParams += "&enabledTLSProtocols=${enabledTlsProtocols.joinToString(",")}"
        }

        if (allow_public_key_retrieval) {
          queryParams += "&allowPublicKeyRetrieval=true"
        }

        jdbc_url_query_parameters.entries.forEach { (key, value) ->
          queryParams += "&$key=$value"
        }

        if(mysql_use_aws_secret_for_credentials) {
          "jdbc-secretsmanager:mysql://${config.host}:${config.port}/${config.database}$queryParams"
        }
        else {
          "jdbc:tracing:mysql://${config.host}:${config.port}/${config.database}$queryParams"
        }
      }
      DataSourceType.HSQLDB -> {
        "jdbc:hsqldb:mem:${database!!};sql.syntax_mys=true"
      }
      DataSourceType.COCKROACHDB, DataSourceType.POSTGRESQL -> {
        var params = "ssl=false&user=${config.username}"
        if (deployment.isTest || deployment.isLocalDevelopment) {
          params += "&createDatabaseIfNotExist=true"
        }
        "jdbc:postgresql://${config.host}:${config.port}/${config.database}?$params"
      }
    }
  }

  private fun getStorePath(url: String?, path: String?): String? {
    var pathToUse: String? = null
    if (!url.isNullOrBlank()) {
      pathToUse = url.split("file://").last()
    } else if (!path.isNullOrBlank()) {
      pathToUse = path
    }

    return pathToUse
  }

  fun asReplica(): DataSourceConfig {
    if (this.type == DataSourceType.COCKROACHDB || this.type == DataSourceType.TIDB) {
      // Cockroach doesn't support replica reads
      return this
    }

    if (this.type != DataSourceType.VITESS_MYSQL) {
      throw Exception("Replica database config only available for VITESS_MYSQL type")
    }

    return DataSourceConfig(
      this.type,
      this.host,
      this.port,
      "@replica",
      this.username,
      this.password,
      this.fixed_pool_size,
      this.connection_timeout,
      this.validation_timeout,
      this.connection_idle_timeout,
      this.connection_max_lifetime,
      this.query_timeout,
      this.keepalive_time,
      this.migrations_resource,
      this.migrations_resources,
      this.migrations_resources_exclusion,
      this.migrations_resources_regex,
      this.vitess_schema_resource_root,
      this.trust_certificate_key_store_url,
      this.trust_certificate_key_store_password,
      this.client_certificate_key_store_url,
      this.client_certificate_key_store_password,
      this.trust_certificate_key_store_path,
      this.client_certificate_key_store_path,
      this.verify_server_identity,
      this.enabledTlsProtocols,
      this.show_sql,
      this.generate_hibernate_stats,
      migrations_format = this.migrations_format
    )
  }

  fun canRecoverOnReplica() = this.type in listOf(
    DataSourceType.COCKROACHDB,
    DataSourceType.TIDB,
  )

}

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

data class DeclarativeSchemaConfig @JvmOverloads constructor(
  /**
   * List of tables to exclude from schema validation
   */
  val excluded_tables: List<String> = listOf(),
)
