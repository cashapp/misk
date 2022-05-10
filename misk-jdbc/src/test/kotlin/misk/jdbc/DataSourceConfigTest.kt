package misk.jdbc

import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.containers.ContainerUtil
import kotlin.test.assertEquals

class DataSourceConfigTest {
  @Test
  fun buildVitessJDBCUrlNoSSL() {
    val config = DataSourceConfig(DataSourceType.VITESS_MYSQL)
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:27003/@master?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=false&useUnicode=true&jdbcCompliantTruncation=false&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun buildVitessJDBCUrlWithTruststore() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      trust_certificate_key_store_url = "path/to/truststore",
      trust_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:27003/@master?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=false&useUnicode=true&jdbcCompliantTruncation=false&" +
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
      client_certificate_key_store_url = "path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:27003/@master?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=false&useUnicode=true&jdbcCompliantTruncation=false&" +
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
      trust_certificate_key_store_url = "path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_url = "path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:27003/@master?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=false&useUnicode=true&jdbcCompliantTruncation=false&" +
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
      trust_certificate_key_store_path = "path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_path = "path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:27003/@master?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=false&useUnicode=true&jdbcCompliantTruncation=false&" +
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
      trust_certificate_key_store_url = "file://path/to/truststore",
      trust_certificate_key_store_password = "changeit",
      client_certificate_key_store_url = "file://path/to/keystore",
      client_certificate_key_store_password = "changeit"
    )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:27003/@master?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=false&useUnicode=true&jdbcCompliantTruncation=false&" +
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
}
