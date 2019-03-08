package misk.jdbc

import misk.config.Config
import misk.environment.Environment
import java.time.Duration

/** Defines a type of datasource */
enum class DataSourceType(
  val driverClassName: String,
  val hibernateDialect: String
) {
  MYSQL(
      driverClassName = "io.opentracing.contrib.jdbc.TracingDriver",
      hibernateDialect = "org.hibernate.dialect.MySQL57Dialect"
  ),
  HSQLDB(
      driverClassName = "org.hsqldb.jdbcDriver",
      hibernateDialect = "org.hibernate.dialect.H2Dialect"
  ),
  VITESS(
      // TODO: Switch back to mysql protocol when this issue is fixed: https://github.com/vitessio/vitess/issues/4100
      // Find the correct buildJdbcUrl and port in the git history
      driverClassName = "io.vitess.jdbc.VitessDriver",
      hibernateDialect = "misk.hibernate.VitessDialect"
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
  val migrations_resource: String? = null,
  val migrations_resources: List<String>? = null,
  val vitess_schema_dir: String? = null,
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
  val show_sql: String? = "false"
) {
  fun withDefaults(): DataSourceConfig {
    return when (type) {
      DataSourceType.MYSQL -> {
        copy(
            port = port ?: 3306,
            host = host ?: "127.0.0.1",
            database = database ?: ""
        )
      }
      DataSourceType.HSQLDB -> {
        this
      }
      DataSourceType.VITESS -> {
        copy(
            port = port ?: 27001,
            host = host ?: "127.0.0.1",
            database = database ?: ""
        )
      }
    }
  }

  fun buildJdbcUrl(env: Environment): String {
    val config = withDefaults()

    require(config.client_certificate_key_store_path.isNullOrBlank() || config.client_certificate_key_store_url.isNullOrBlank()) {
      "can optionally set client_certificate_key_store_path or client_certificate_key_store_url, but not both"
    }

    require(config.trust_certificate_key_store_path.isNullOrBlank() || config.trust_certificate_key_store_url.isNullOrBlank()) {
      "can optionally set trust_certificate_key_store_path or trust_certificate_key_store_url, but not both"
    }

    return when (type) {
      DataSourceType.MYSQL -> {
        var queryParams = "?useLegacyDatetimeCode=false"
        if (env == Environment.TESTING || env == Environment.DEVELOPMENT) {
          queryParams += "&createDatabaseIfNotExist=true"
        }

        var trustStoreUrl: String? = null
        if (!config.trust_certificate_key_store_path.isNullOrBlank()) {
          trustStoreUrl = "file://${config.trust_certificate_key_store_path}"
        } else if (!config.trust_certificate_key_store_url.isNullOrBlank()) {
          trustStoreUrl = config.trust_certificate_key_store_url
        }

        // TODO(rhall): share this with DataSource config in SessionFactoryService.
        // https://github.com/square/misk/issues/397
        // Explicitly not updating VITESS below since this is a temporary hack until the above
        // issue is resolved.
        if (!trustStoreUrl.isNullOrBlank()) {
          require(!config.trust_certificate_key_store_password.isNullOrBlank()) {
            "must provide a trust_certificate_key_store_password"
          }
          queryParams += "&trustCertificateKeyStoreUrl=${trustStoreUrl}"
          queryParams += "&trustCertificateKeyStorePassword=${config.trust_certificate_key_store_password}"
          queryParams += "&verifyServerCertificate=true"
          queryParams += "&useSSL=true"
          queryParams += "&requireSSL=true"
        }
        "jdbc:tracing:mysql://${config.host}:${config.port}/${config.database}$queryParams"
      }
      DataSourceType.HSQLDB -> {
        "jdbc:hsqldb:mem:${database!!};sql.syntax_mys=true"
      }
      DataSourceType.VITESS -> {
        var queryParams = ""
        var useSSL = false

        // NOTE(nb): still support url properties in Vitess for backwards compatibility.
        val trustStorePath = getStorePath(config.trust_certificate_key_store_url, config.trust_certificate_key_store_path)
        val certStorePath = getStorePath(config.client_certificate_key_store_url, config.client_certificate_key_store_path)

        /**
         * Query params for VitessJDBC driver look like default MySQL JDBC driver query params
         * but are named slightly differently and a smaller subset are supported. See
         * [io.vitess.jdbc.VitessJDBCUrl] for the complete list
         */
        if (!trustStorePath.isNullOrBlank()) {
          require(!config.trust_certificate_key_store_password.isNullOrBlank()) {
            "must provide a trust_certificate_key_store_password if trust_certificate_key_store_url" +
                " or trust_certificate_key_store_path is set"
          }
          queryParams += "${if (queryParams.isEmpty()) "?" else "&"}trustStore=${trustStorePath}"
          queryParams += "&trustStorePassword=${config.trust_certificate_key_store_password}"
          useSSL = true
        }
        if (!certStorePath.isNullOrBlank()) {
          require(!config.client_certificate_key_store_password.isNullOrBlank()) {
            "must provide a client_certificate_key_store_password if client_certificate_key_store_url" +
                " or client_certificate_key_store_path is set"
          }
          queryParams += "${if (queryParams.isEmpty()) "?" else "&"}keyStore=${certStorePath}"
          queryParams += "&keyStorePassword=${config.client_certificate_key_store_password}"
          useSSL = true
        }
        if (useSSL) {
          queryParams += "&useSSL=true"
        }

        "jdbc:vitess://${config.host}:${config.port}/${config.database}$queryParams"
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
