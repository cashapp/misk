package misk.jdbc

import wisp.config.Config
import wisp.deployment.Deployment
import java.io.File
import java.time.Duration

/** Defines a type of datasource */
enum class DataSourceType(
  val driverClassName: String,
  val hibernateDialect: String,
  val isVitess: Boolean
) {
  MYSQL(
    driverClassName = "io.opentracing.contrib.jdbc.TracingDriver",
    hibernateDialect = "org.hibernate.dialect.MySQL57Dialect",
    isVitess = false
  ),
  HSQLDB(
    driverClassName = "org.hsqldb.jdbcDriver",
    hibernateDialect = "org.hibernate.dialect.H2Dialect",
    isVitess = false
  ),
  VITESS_MYSQL(
    driverClassName = MYSQL.driverClassName,
    hibernateDialect = "misk.hibernate.VitessDialect",
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
    hibernateDialect = "org.hibernate.dialect.MySQL57Dialect",
    isVitess = false
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
  val fixed_pool_size: Int = 10,
  val connection_timeout: Duration = Duration.ofSeconds(10),
  val validation_timeout: Duration = Duration.ofSeconds(3),
  val connection_max_lifetime: Duration = Duration.ofMinutes(1),
  val query_timeout: Duration? = Duration.ofMinutes(1),
  val migrations_resource: String? = null,
  val migrations_resources: List<String>? = null,
  val vitess_schema_resource_root: String? = null,
  /*
     See https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-using-ssl.html for
     trust_certificate_key_store_* details.
   */
  val trust_certificate_key_store_url: String? = null,
  val trust_certificate_key_store_password: String? = null,
  val client_certificate_key_store_url: String? = null,
  val client_certificate_key_store_password: String? = null,
  // Vitess driver doesn't support passing in URLs so support paths and prefer this for Vitess
  // going forward
  val trust_certificate_key_store_path: String? = null,
  val client_certificate_key_store_path: String? = null,
  val verify_server_identity: Boolean = false,
  val enabledTlsProtocols: List<String> = listOf("TLSv1.2", "TLSv1.3"),
  val show_sql: String? = "false",
  // Consider using this if you want Hibernate to automagically batch inserts/updates when it can.
  val jdbc_statement_batch_size: Int? = null
) {
  fun withDefaults(): DataSourceConfig {
    val isRunningInDocker = File("/proc/1/cgroup")
      .takeIf { it.exists() }?.useLines { lines ->
        lines.any { it.contains("/docker") }
      } ?: false
    val server_hostname = if (isRunningInDocker)
        "host.docker.internal"
      else
        "127.0.0.1"
    return when (type) {
      DataSourceType.MYSQL -> {
        copy(
          port = port ?: 3306,
          host = host ?: "127.0.0.1",
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
          host = host ?: server_hostname,
          database = database ?: "@master"
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
          // TODO(jontirsen): Try turning on server side prepared statements again when this issue
          //  has been fixed: https://github.com/vitessio/vitess/issues/5075
          queryParams += "&useServerPrepStmts=false"
          queryParams += "&useUnicode=true"
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

        "jdbc:tracing:mysql://${config.host}:${config.port}/${config.database}$queryParams"
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
      this.connection_max_lifetime,
      this.query_timeout,
      this.migrations_resource,
      this.migrations_resources,
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
    )
  }

  fun canRecoverOnReplica() = this.type in listOf(
    DataSourceType.COCKROACHDB,
    DataSourceType.TIDB,
    DataSourceType.VITESS_MYSQL
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
