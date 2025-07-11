package misk.jdbc

import misk.vitess.testing.DefaultSettings
import org.junit.jupiter.api.Test
import misk.containers.ContainerUtil
import wisp.deployment.TESTING
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataSourceConfigTest {
  private val dockerVitessPort = DefaultSettings.PORT;

  @Test
  fun buildVitessJDBCUrlNoSSL() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort)
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&useUnicode=true&jdbcCompliantTruncation=false&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildVitessJDBCUrlWithTruststore() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort,
      trust_certificate_key_store_url = "path/to/truststore",
      trust_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&useUnicode=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildVitessJDBCUrlWithKeystore() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort,
      client_certificate_key_store_url = "path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&useUnicode=true&jdbcCompliantTruncation=false&" +
        "clientCertificateKeyStoreUrl=path/to/keystore&clientCertificateKeyStorePassword=" +
        "changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildVitessJDBCUrlWithFullTLS() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort,
      trust_certificate_key_store_url = "path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_url = "path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&useUnicode=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=path/to/truststore&trustCertificateKeyStorePassword=" +
        "changeit&clientCertificateKeyStoreUrl=path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildVitessJDBCUrlWithPath() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort,
      trust_certificate_key_store_path = "path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_path = "path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&useUnicode=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&trustCertificateKeyStorePassword" +
        "=changeit&clientCertificateKeyStoreUrl=file://path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildVitessJDBCUrlWithActualUrl() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort,
      trust_certificate_key_store_url = "file://path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_url = "file://path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&useUnicode=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&clientCertificateKeyStoreUrl=" +
        "file://path/to/keystore&clientCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithTruststoreViaUrl() {
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      trust_certificate_key_store_url = "file://path/to/truststore",
      trust_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithTruststoreViaPath() {
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      trust_certificate_key_store_path = "path/to/truststore",
      trust_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithKeystoreAndTruststoreUrlsAndVerifyIdentity() {
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      trust_certificate_key_store_url = "file://path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_url = "file://path/to/keystore",
      client_certificate_key_store_password = "changeit",
      verify_server_identity = true
    )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&" +
        "clientCertificateKeyStoreUrl=file://path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&" +
        "sslMode=VERIFY_IDENTITY&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTls() {
    val config = DataSourceConfig(DataSourceType.MYSQL)
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTlsAllowPublicKeyRetrieval() {
    val config = DataSourceConfig(DataSourceType.MYSQL, allow_public_key_retrieval = true)
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3&allowPublicKeyRetrieval=true",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTlsCustomJdbcUrlParameters() {
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      jdbc_url_query_parameters = mapOf("alpha" to "true", "bravo" to "false")
    )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3&alpha=true&bravo=false",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithEnabledTlsProtocols() {
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      trust_certificate_key_store_url = "file://path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_url = "file://path/to/keystore",
      client_certificate_key_store_password = "changeit",
      verify_server_identity = true,
      enabledTlsProtocols = listOf("TLSv1.3"),
    )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&" +
        "clientCertificateKeyStoreUrl=file://path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&" +
        "sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun errorWhenDeclarativeUsedOnUnsupportedDBs() {
    DataSourceType.entries
      .filter { it != DataSourceType.MYSQL }
      .forEach {
        assertFailsWith<IllegalArgumentException> {
          DataSourceConfig(it, migrations_format = MigrationsFormat.DECLARATIVE)
        }
      }
  }

  @Test
  fun errorWhenTraditionalUsesDeclarativeSchemaConfig() {
    assertFailsWith<IllegalArgumentException> {
      DataSourceConfig(DataSourceType.MYSQL,
        migrations_format = MigrationsFormat.TRADITIONAL,
        declarative_schema_config = DeclarativeSchemaConfig(listOf("table")),
      )
    }
  }
}
