package misk.jdbc

import misk.environment.Environment
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DataSourceConfigTest {
  @Test
  fun buildVitessJDBCUrlNoSSL() {
    val config = DataSourceConfig(DataSourceType.VITESS)
    assertEquals("jdbc:vitess://127.0.0.1:27001/", config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildVitessJDBCUrlWithTruststore() {
    val config = DataSourceConfig(DataSourceType.VITESS,
        trust_certificate_key_store_url = "path/to/truststore",
        trust_certificate_key_store_password = "changeit")
    assertEquals("jdbc:vitess://127.0.0.1:27001/?trustStore=path/to/truststore&" +
        "trustStorePassword=changeit&useSSL=true",
        config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildVitessJDBCUrlWithKeystore() {
    val config = DataSourceConfig(DataSourceType.VITESS,
        client_certificate_key_store_url = "path/to/keystore",
        client_certificate_key_store_password = "changeit")
    assertEquals("jdbc:vitess://127.0.0.1:27001/?keyStore=path/to/keystore&" +
        "keyStorePassword=changeit&useSSL=true",
        config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildVitessJDBCUrlWithFullTLS() {
    val config = DataSourceConfig(DataSourceType.VITESS,
        trust_certificate_key_store_url = "path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_url = "path/to/keystore",
        client_certificate_key_store_password = "changeit")
    assertEquals("jdbc:vitess://127.0.0.1:27001/?trustStore=path/to/truststore&" +
        "trustStorePassword=changeit&keyStore=path/to/keystore&" +
        "keyStorePassword=changeit&useSSL=true",
        config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildVitessJDBCUrlWithPath() {
    val config = DataSourceConfig(DataSourceType.VITESS,
        trust_certificate_key_store_path = "path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_path = "path/to/keystore",
        client_certificate_key_store_password = "changeit")
    assertEquals("jdbc:vitess://127.0.0.1:27001/?trustStore=path/to/truststore&" +
        "trustStorePassword=changeit&keyStore=path/to/keystore&" +
        "keyStorePassword=changeit&useSSL=true", config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildVitessJDBCUrlWithActualUrl() {
    val config = DataSourceConfig(DataSourceType.VITESS,
        trust_certificate_key_store_url = "file://path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_url = "file://path/to/keystore",
        client_certificate_key_store_password = "changeit")
    assertEquals("jdbc:vitess://127.0.0.1:27001/?trustStore=path/to/truststore&" +
        "trustStorePassword=changeit&keyStore=path/to/keystore&" +
        "keyStorePassword=changeit&useSSL=true", config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildMysqlJDBCUrlWithTruststoreViaUrl() {
    val config = DataSourceConfig(DataSourceType.MYSQL,
        trust_certificate_key_store_url = "file://path/to/truststore",
        trust_certificate_key_store_password = "changeit")
    assertEquals("jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA",
        config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildMysqlJDBCUrlWithTruststoreViaPath() {
    val config = DataSourceConfig(DataSourceType.MYSQL,
        trust_certificate_key_store_path = "path/to/truststore",
        trust_certificate_key_store_password = "changeit")
    assertEquals("jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA",
        config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildMysqlJDBCUrlWithKeystoreAndTruststoreUrlsAndVerifyIdentity() {
    val config = DataSourceConfig(DataSourceType.MYSQL,
        trust_certificate_key_store_url = "file://path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_url = "file://path/to/keystore",
        client_certificate_key_store_password = "changeit",
        verify_server_identity = true)
    assertEquals("jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&" +
        "clientCertificateKeyStoreUrl=file://path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&" +
        "sslMode=VERIFY_IDENTITY",
        config.buildJdbcUrl(Environment.TESTING))
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTls() {
    val config = DataSourceConfig(DataSourceType.MYSQL)
    assertEquals("jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED",
        config.buildJdbcUrl(Environment.TESTING))
  }
}
