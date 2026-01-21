package misk.jdbc

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import misk.containers.ContainerUtil
import misk.vitess.testing.DefaultSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
import wisp.deployment.TESTING

class DataSourceConfigTest {
  private val dockerVitessPort = DefaultSettings.PORT

  @Test
  fun buildVitessJDBCUrlNoSSL() {
    val config = DataSourceConfig(DataSourceType.VITESS_MYSQL, port = dockerVitessPort)
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildVitessJDBCUrlWithTruststore() {
    val config =
      DataSourceConfig(
        DataSourceType.VITESS_MYSQL,
        port = dockerVitessPort,
        trust_certificate_key_store_url = "path/to/truststore",
        trust_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildVitessJDBCUrlWithKeystore() {
    val config =
      DataSourceConfig(
        DataSourceType.VITESS_MYSQL,
        port = dockerVitessPort,
        client_certificate_key_store_url = "path/to/keystore",
        client_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
        "clientCertificateKeyStoreUrl=path/to/keystore&clientCertificateKeyStorePassword=" +
        "changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildVitessJDBCUrlWithFullTLS() {
    val config =
      DataSourceConfig(
        DataSourceType.VITESS_MYSQL,
        port = dockerVitessPort,
        trust_certificate_key_store_url = "path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_url = "path/to/keystore",
        client_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=path/to/truststore&trustCertificateKeyStorePassword=" +
        "changeit&clientCertificateKeyStoreUrl=path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildVitessJDBCUrlWithPath() {
    val config =
      DataSourceConfig(
        DataSourceType.VITESS_MYSQL,
        port = dockerVitessPort,
        trust_certificate_key_store_path = "path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_path = "path/to/keystore",
        client_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&trustCertificateKeyStorePassword" +
        "=changeit&clientCertificateKeyStoreUrl=file://path/to/keystore&" +
        "clientCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildVitessJDBCUrlWithActualUrl() {
    val config =
      DataSourceConfig(
        DataSourceType.VITESS_MYSQL,
        port = dockerVitessPort,
        trust_certificate_key_store_url = "file://path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_url = "file://path/to/keystore",
        client_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://${ContainerUtil.dockerTargetOrLocalIp()}:$dockerVitessPort/@primary?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "useServerPrepStmts=true&jdbcCompliantTruncation=false&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&clientCertificateKeyStoreUrl=" +
        "file://path/to/keystore&clientCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithTruststoreViaUrl() {
    val config =
      DataSourceConfig(
        DataSourceType.MYSQL,
        trust_certificate_key_store_url = "file://path/to/truststore",
        trust_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithTruststoreViaPath() {
    val config =
      DataSourceConfig(
        DataSourceType.MYSQL,
        trust_certificate_key_store_path = "path/to/truststore",
        trust_certificate_key_store_password = "changeit",
      )
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "trustCertificateKeyStoreUrl=file://path/to/truststore&" +
        "trustCertificateKeyStorePassword=changeit&sslMode=VERIFY_CA&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithKeystoreAndTruststoreUrlsAndVerifyIdentity() {
    val config =
      DataSourceConfig(
        DataSourceType.MYSQL,
        trust_certificate_key_store_url = "file://path/to/truststore",
        trust_certificate_key_store_password = "changeit",
        client_certificate_key_store_url = "file://path/to/keystore",
        client_certificate_key_store_password = "changeit",
        verify_server_identity = true,
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
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTls() {
    val config = DataSourceConfig(DataSourceType.MYSQL)
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTlsAllowPublicKeyRetrieval() {
    val config = DataSourceConfig(DataSourceType.MYSQL, allow_public_key_retrieval = true)
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3&allowPublicKeyRetrieval=true",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithNoTlsCustomJdbcUrlParameters() {
    val config =
      DataSourceConfig(DataSourceType.MYSQL, jdbc_url_query_parameters = mapOf("alpha" to "true", "bravo" to "false"))
    assertEquals(
      "jdbc:tracing:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&sslMode=PREFERRED&" +
        "enabledTLSProtocols=TLSv1.2,TLSv1.3&alpha=true&bravo=false",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun buildMysqlJDBCUrlWithEnabledTlsProtocols() {
    val config =
      DataSourceConfig(
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
      config.buildJdbcUrl(TESTING),
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
      DataSourceConfig(
        DataSourceType.MYSQL,
        migrations_format = MigrationsFormat.TRADITIONAL,
        declarative_schema_config = DeclarativeSchemaConfig(listOf("table")),
      )
    }
  }

  @Test
  fun externallyManagedMigrationsFormatIsValid() {
    // Should not throw any exception
    val config = DataSourceConfig(DataSourceType.MYSQL, migrations_format = MigrationsFormat.EXTERNALLY_MANAGED)
    assertThat(config.migrations_format).isEqualTo(MigrationsFormat.EXTERNALLY_MANAGED)
  }

  @Test
  fun externallyManagedMigrationsCanUseDeclarativeSchemaConfig() {
    // Should not throw any exception - EXTERNALLY_MANAGED can use declarative_schema_config
    val config =
      DataSourceConfig(
        DataSourceType.MYSQL,
        migrations_format = MigrationsFormat.EXTERNALLY_MANAGED,
        declarative_schema_config = DeclarativeSchemaConfig(listOf("table")),
      )
    assertThat(config.migrations_format).isEqualTo(MigrationsFormat.EXTERNALLY_MANAGED)
    assertThat(config.declarative_schema_config?.excluded_tables).containsExactly("table")
  }

  @Test
  @SetEnvironmentVariable(key = "AWS_REGION", value = "us-east-1")
  fun buildMysqlJDBCUrlUsingAWSSecretForCredential() {
    val config =
      DataSourceConfig(
        DataSourceType.MYSQL,
        mysql_use_aws_secret_for_credentials = true,
        mysql_aws_secret_name = "secret_name",
      )
    assertEquals(
      "jdbc-secretsmanager:mysql://127.0.0.1:3306/?useLegacyDatetimeCode=false&" +
        "createDatabaseIfNotExist=true&connectTimeout=10000&socketTimeout=60000&" +
        "sslMode=PREFERRED&enabledTLSProtocols=TLSv1.2,TLSv1.3",
      config.buildJdbcUrl(TESTING),
    )
  }

  @Test
  fun testAwsSecretsManagerDriverSelection() {
    val config =
      DataSourceConfig(
        type = DataSourceType.MYSQL,
        mysql_use_aws_secret_for_credentials = true,
        mysql_aws_secret_name = "test-secret",
      )

    // Should use AWS Secrets Manager driver instead of TracingDriver
    assertThat(config.getDriverClassName()).isEqualTo("com.amazonaws.secretsmanager.sql.AWSSecretsManagerMySQLDriver")

    // Should generate correct JDBC URL
    val jdbcUrl = config.buildJdbcUrl(TESTING)
    assertThat(jdbcUrl).startsWith("jdbc-secretsmanager:mysql://")
  }

  @Test
  fun testNormalMysqlStillUsesTracingDriver() {
    val config = DataSourceConfig(type = DataSourceType.MYSQL, mysql_use_aws_secret_for_credentials = false)

    // Should use TracingDriver for normal MySQL
    assertThat(config.getDriverClassName()).isEqualTo("io.opentracing.contrib.jdbc.TracingDriver")

    // Should generate correct JDBC URL
    val jdbcUrl = config.buildJdbcUrl(TESTING)
    assertThat(jdbcUrl).startsWith("jdbc:tracing:mysql://")
  }

  @Test
  fun testTransactionIsolationLevelEnum() {
    // Test enum values match JDBC constants
    assertThat(TransactionIsolationLevel.READ_UNCOMMITTED.jdbcValue).isEqualTo(1)
    assertThat(TransactionIsolationLevel.READ_COMMITTED.jdbcValue).isEqualTo(2)
    assertThat(TransactionIsolationLevel.REPEATABLE_READ.jdbcValue).isEqualTo(4)
    assertThat(TransactionIsolationLevel.SERIALIZABLE.jdbcValue).isEqualTo(8)

    // Test HikariCP values
    assertThat(TransactionIsolationLevel.READ_UNCOMMITTED.hikariValue).isEqualTo("TRANSACTION_READ_UNCOMMITTED")
    assertThat(TransactionIsolationLevel.READ_COMMITTED.hikariValue).isEqualTo("TRANSACTION_READ_COMMITTED")
    assertThat(TransactionIsolationLevel.REPEATABLE_READ.hikariValue).isEqualTo("TRANSACTION_REPEATABLE_READ")
    assertThat(TransactionIsolationLevel.SERIALIZABLE.hikariValue).isEqualTo("TRANSACTION_SERIALIZABLE")
  }

  @Test
  fun testDefaultTransactionIsolationConfiguration() {
    // Config without isolation level should have null default
    val configWithoutIsolation = DataSourceConfig(type = DataSourceType.MYSQL)
    assertThat(configWithoutIsolation.default_transaction_isolation).isNull()

    // Config with isolation level should preserve it
    val configWithIsolation = DataSourceConfig(
      type = DataSourceType.MYSQL,
      default_transaction_isolation = TransactionIsolationLevel.READ_COMMITTED
    )
    assertThat(configWithIsolation.default_transaction_isolation).isEqualTo(TransactionIsolationLevel.READ_COMMITTED)
  }

  @Test
  fun testReplicaConfigPreservesTransactionIsolation() {
    val originalConfig = DataSourceConfig(
      type = DataSourceType.VITESS_MYSQL,
      default_transaction_isolation = TransactionIsolationLevel.SERIALIZABLE
    )

    val replicaConfig = originalConfig.asReplica()

    assertThat(replicaConfig.default_transaction_isolation).isEqualTo(TransactionIsolationLevel.SERIALIZABLE)
    assertThat(replicaConfig.database).isEqualTo("@replica")
  }
}
