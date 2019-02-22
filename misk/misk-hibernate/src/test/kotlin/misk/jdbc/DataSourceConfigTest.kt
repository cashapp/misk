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
}
