package misk.jdbc

import misk.vitess.testing.DefaultSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import misk.containers.ContainerUtil
import org.junitpioneer.jupiter.SetEnvironmentVariable
import wisp.deployment.TESTING
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataSourceConfigTest {
  private val dockerVitessPort = DefaultSettings.PORT

  @Test
  fun buildVitessJDBCUrlNoSSL() {
    val config = DataSourceConfig(
      DataSourceType.VITESS_MYSQL,
      port = dockerVitessPort)
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&sslMode=PREFERRED&" +
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
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
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
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
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
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
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
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
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
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
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

  @Test
  fun externallyManagedMigrationsFormatIsValid() {
    // Should not throw any exception
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      migrations_format = MigrationsFormat.EXTERNALLY_MANAGED
    )
    assertThat(config.migrations_format).isEqualTo(MigrationsFormat.EXTERNALLY_MANAGED)
  }

  @Test
  fun externallyManagedMigrationsCanUseDeclarativeSchemaConfig() {
    // Should not throw any exception - EXTERNALLY_MANAGED can use declarative_schema_config
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      migrations_format = MigrationsFormat.EXTERNALLY_MANAGED,
      declarative_schema_config = DeclarativeSchemaConfig(listOf("table"))
    )
    assertThat(config.migrations_format).isEqualTo(MigrationsFormat.EXTERNALLY_MANAGED)
    assertThat(config.declarative_schema_config?.excluded_tables).containsExactly("table")
  }

  @Test
  @SetEnvironmentVariable(key = "AWS_REGION", value = "us-east-1")
  fun buildMysqlJDBCUrlUsingAWSSecretForCredential() {
    val config = DataSourceConfig(
      DataSourceType.MYSQL,
      mysql_use_aws_secret_for_credentials = true,
      mysql_aws_secret_name = "secret_name",
    )
    assertEquals(
      "jdbc-secretsmanager:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
          "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
          "sslMode=PREFERRED&enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING)
    )
  }

  @Test
  fun testAwsSecretsManagerDriverSelection() {
    val config = DataSourceConfig(
      type = DataSourceType.MYSQL,
      mysql_use_aws_secret_for_credentials = true,
      mysql_aws_secret_name = "test-secret"
    )

    // Should use AWS Secrets Manager driver instead of TracingDriver
    assertThat(config.getDriverClassName())
      .isEqualTo("com.amazonaws.secretsmanager.sql.AWSSecretsManagerMySQLDriver")

    // Should generate correct JDBC URL
    val jdbcUrl = config.buildJdbcUrl(TESTING)
    assertThat(jdbcUrl).startsWith("jdbc-secretsmanager:mysql://")
  }

  @Test
  fun testNormalMysqlStillUsesTracingDriver() {
    val config = DataSourceConfig(
      type = DataSourceType.MYSQL,
      mysql_use_aws_secret_for_credentials = false
    )

    // Should use TracingDriver for normal MySQL
    assertThat(config.getDriverClassName())
      .isEqualTo("io.opentracing.contrib.jdbc.TracingDriver")

    // Should generate correct JDBC URL
    val jdbcUrl = config.buildJdbcUrl(TESTING)
    assertThat(jdbcUrl).startsWith("jdbc:tracing:mysql://")
  }
}
