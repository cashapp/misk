package misk.jdbc

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.database.StartDatabaseService
import misk.environment.DeploymentModule
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import java.sql.SQLException
import kotlin.test.assertFailsWith

@MiskTest(startService = false)
internal class DeclarativeSchemaMigratorTest {

  val appConfig = MiskConfig.load<RootConfig>("test_declarative_schemamigrator_app", TESTING)
  val config = appConfig.mysql_data_source

  @MiskTestModule
  val deploymentModule = DeploymentModule(TESTING)

  @MiskTestModule
  val testingModule = MiskTestingServiceModule()

  @MiskTestModule
  val jdbcModule = JdbcModule(Movies::class, config)

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
  ) : Config

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var dataSourceService: DataSourceService
  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  @Inject @Movies lateinit var startDatabaseService: StartDatabaseService
  private lateinit var declarativeSchemaMigrator : DeclarativeSchemaMigrator

  @BeforeEach
  internal fun setUp() {
    declarativeSchemaMigrator = schemaMigrator as DeclarativeSchemaMigrator
    startDatabaseService.startAsync()
    startDatabaseService.awaitRunning()
    dataSourceService.startAsync()
    dataSourceService.awaitRunning()

    dropTables()
  }

  @AfterEach
  internal fun tearDown() {
    if (dataSourceService.isRunning) {
      dropTables()
      dataSourceService.stopAsync()
      dataSourceService.awaitTerminated()
    }
    if (startDatabaseService.isRunning) {
      startDatabaseService.stopAsync()
      startDatabaseService.awaitTerminated()
    }
  }

  private fun dropTables() {
    dataSourceService.dataSource.connection.use { connection ->
      val statement = connection.createStatement()
      statement.addBatch("DROP TABLE IF EXISTS schema_version")
      statement.addBatch("DROP TABLE IF EXISTS table_1")
      statement.addBatch("DROP TABLE IF EXISTS table_2")
      statement.addBatch("DROP TABLE IF EXISTS library_table")
      statement.executeBatch()
      connection.commit()
    }
  }

  private fun tableExists(table: String): Boolean {
    try {
      dataSourceService.dataSource.connection.use { connection ->
        connection.createStatement().use {
          it.execute("SELECT * FROM $table LIMIT 1")
        }
      }
      return true
    } catch (e: SQLException) {
      return false
    }
  }

  @Test
  fun doesNothingWhenNoMigrations() {
    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Empty)
  }

  @Test
  fun appliesMigrations() {
    val mainSource = config.migrations_resources!![0]
    val librarySource = config.migrations_resources!![1]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        |CREATE TABLE table_1 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/t2.sql", """
        |CREATE TABLE table_2 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    resourceLoader.put(
      "$librarySource/name/space/t3.sql", """
        |CREATE TABLE library_table (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    assertThat(tableExists("table_1")).isFalse
    assertThat(tableExists("table_2")).isFalse
    assertThat(tableExists("library_table")).isFalse

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    assertThat(tableExists("table_1")).isTrue
    assertThat(tableExists("table_2")).isTrue
    assertThat(tableExists("library_table")).isTrue

    // Second run should be idepmpotent
    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    assertThat(tableExists("table_1")).isTrue
    assertThat(tableExists("table_2")).isTrue
    assertThat(tableExists("library_table")).isTrue
  }

  @Test
  fun failsOnInvalidMigrations() {
    val mainSource = config.migrations_resources!![0]
    resourceLoader.put(
      "$mainSource/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin()
    )

    assertFailsWith<IllegalArgumentException>(message = "unexpected resource: $mainSource/v1001__movies.sql") {
      declarativeSchemaMigrator.applyAll("test")
    }

    assertThat(tableExists("table_1")).isFalse
  }

  @Test
  fun skipsExcludedMigrations() {
    val mainSource = config.migrations_resources!![0]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        |CREATE TABLE table_1 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/all-migrations.sql", """
        |CREATE TABLE table_2 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    assertThat(tableExists("table_1")).isTrue
    assertThat(tableExists("table_2")).isFalse
  }

  @Test
  fun doesNothingRequireAllMigrations() {
    assertThat(declarativeSchemaMigrator.requireAll()).isEqualTo(MigrationStatus.Success)
  }

  @Test
  fun passRequireAllMigrations() {
    val mainSource = config.migrations_resources!![0]
    val librarySource = config.migrations_resources!![1]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        |CREATE TABLE table_1 (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )
    resourceLoader.put(
      "$mainSource/t2.sql", """
        |CREATE TABLE `table_2` (`id` bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    resourceLoader.put(
      "$librarySource/name/space/t3.sql", """
        |CREATE TABLE library_table (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    assertThat(tableExists("table_1")).isFalse
    assertThat(tableExists("table_2")).isFalse
    assertThat(tableExists("library_table")).isFalse

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    // Verify migration worked!
    assertThat(declarativeSchemaMigrator.requireAll()).isEqualTo(MigrationStatus.Success)

  }

  @Test
  fun testSqlParsing() {
    val mainSource = config.migrations_resources!![0]
    val librarySource = config.migrations_resources!![1]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        create table table_1 (
        channel_id varchar(255) not null,
        channel_name varchar(255) not null,
        user_slack_id varchar(255) not null,
        this_record_updated_at bigint(10),
        primary key (channel_id, user_slack_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
        """.trimMargin()
    )
    resourceLoader.put(
      "$mainSource/t2.sql", """
        CREATE TABLE table_2 (
        id                          bigint       NOT NULL AUTO_INCREMENT,
        created_at                  timestamp(3) NOT NULL DEFAULT NOW(3),
        updated_at                  timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
        version                     bigint       NOT NULL DEFAULT 0,
        token                       varchar(191) NOT NULL,
        customer_token              varchar(191) NOT NULL,
        customer_id                 bigint       NOT NULL,
        source_stored_balance_token varchar(191) NOT NULL,
        state                       varchar(191) NOT NULL,
        payment_request             text         NOT NULL,
        unit                        bigint       NOT NULL,
        currency                    varchar(191) NOT NULL,
        fiat_value_units            bigint       NOT NULL,
        fiat_value_currency         varchar(191) NOT NULL,
        usd_equivalent_cents        bigint       NOT NULL,
        transaction_token           varchar(191),
        PRIMARY KEY (id),
        UNIQUE KEY idx_token(token),
        KEY idx_customer_token(customer_token),
        KEY idx_transaction_token(transaction_token)
    )
        ENGINE = InnoDB
        DEFAULT CHARSET = utf8mb4
        ROW_FORMAT = DYNAMIC;
        """.trimMargin()
    )

    resourceLoader.put(
      "$librarySource/name/space/t3.sql", """
        CREATE TABLE library_table (
        id bigint NOT NULL AUTO_INCREMENT,
        token_1 varchar(191) NOT NULL,
        token_2 varchar(191) NOT NULL,
        postal_codes varchar(191) NULL,
        labels varchar(191) NULL,
        source varchar(191) NOT NULL,
        is_primary tinyint NOT NULL,
        validated tinyint NOT NULL,
        address_line_1 varchar(191) NULL,
        address_line_2 varchar(191) NULL,
        administrative_district varchar(191) NULL,
        locality varchar(191) NULL,
        codey varchar(191) NULL,
        delivery varchar(191) NULL,
        created_at timestamp(3) NOT NULL DEFAULT NOW(3),
        updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
        PRIMARY KEY (id),
        UNIQUE KEY idx_token (token_1)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
        """.trimMargin()
    )

    assertThat(tableExists("table_1")).isFalse
    assertThat(tableExists("table_2")).isFalse
    assertThat(tableExists("library_table")).isFalse

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    // Verify migration worked!
    assertThat(declarativeSchemaMigrator.requireAll()).isEqualTo(MigrationStatus.Success)

  }

  @Test
  fun failRequireAlForMissingTable() {
    val librarySource = config.migrations_resources!![1]
    resourceLoader.put(
      "$librarySource/name/space/t3.sql", """
        |CREATE TABLE library_table (id bigint, PRIMARY KEY (id))
        |""".trimMargin()
    )

    assertFailsWith<IllegalStateException>(message = "Error: Table library_table missing in the database.") {
      declarativeSchemaMigrator.requireAll()
    }

    assertThat(tableExists("table_1")).isFalse
  }

  @Test
  fun failRequireAllForMissingColumn() {
    val mainSource = config.migrations_resources!![0]
    resourceLoader.put(
      "$mainSource/name/space/table_1.sql", """
        CREATE TABLE table_1 (
        session_id varchar(36) NOT NULL primary key,
        user_id varchar(24),
        created_at timestamp,
        device_type varchar(24),
        app_version varchar(250),
        time_zone_name varchar(200),
        path varchar(2000),
        api varchar(1000),
        ad_content_id varchar(1000),
        os varchar(100),
        model varchar(500),
        user_agent varchar(500))
        """.trimMargin()
    )

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    resourceLoader.put(
      "$mainSource/name/space/table_1.sql", """
        CREATE TABLE table_1 (
        session_id varchar(36) NOT NULL primary key,
        user_id varchar(24),
        created_at timestamp,
        device_type varchar(24),
        app_version varchar(250),
        time_zone_name varchar(200),
        path varchar(2000),
        api varchar(1000),
        ad_content_id varchar(1000),
        os varchar(100),
        model varchar(500),
        /* 
            Added ip_address column
        */
        ip_address varchar(48),
        user_agent varchar(500))
        """.trimMargin()
    )

    assertFailsWith<IllegalStateException>(message = "Error: Column \"ip_address\" for table table_1 is missing in the database.") {
      declarativeSchemaMigrator.requireAll()
    }
  }

  @Test
  fun failRequireAllForWrongColumnPrecision() {
    val mainSource = config.migrations_resources!![0]
    resourceLoader.put(
      "$mainSource/name/space/table_1.sql", """
        CREATE TABLE table_1 (
        client_event_id varchar(50) NOT NULL,
        user_id varchar(24),
        -- This column will be changed
        device_id varchar(250),
        organization_id varchar(24),
        client_time timestamp NOT NULL,
        category varchar(256),
        name varchar(256),
        client_session_id varchar(60),
        session_id varchar(24),
        client_server_time_skew bigint,
        server_session_id varchar(60),
        model varchar(256),
        os varchar(256))
        """.trimMargin()
    )

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    resourceLoader.put(
      "$mainSource/name/space/table_1.sql", """
        CREATE TABLE table_1 (
        client_event_id varchar(50) NOT NULL,
        user_id varchar(24),
        -- Changed device_id column type
        device_id varchar(300),
        organization_id varchar(24),
        client_time timestamp NOT NULL,
        category varchar(256),
        name varchar(256),
        client_session_id varchar(60),
        session_id varchar(24),
        client_server_time_skew bigint,
        server_session_id varchar(60),
        model varchar(256),
        os varchar(256))
        """.trimMargin()
    )

    assertFailsWith<IllegalStateException>(message = "Error: Column \"device_id\" for table table_1 has incorrect type in the database.") {
      declarativeSchemaMigrator.requireAll()
    }
  }

  @Test
  fun failRequireAllForWrongColumnType() {
    val mainSource = config.migrations_resources!![0]
    resourceLoader.put(
      "$mainSource/name/space/table_1.sql", """
        CREATE TABLE `table_1` (
        `id` bigint(20) NOT NULL AUTO_INCREMENT PRIMARY KEY,
        `lock_version` int(11) NOT NULL DEFAULT '0',
        `riskarbiter_model_name` varchar(255) COLLATE utf8mb4_bin NOT NULL,
        `riskarbiter_model_name_base` varchar(255) COLLATE utf8mb4_bin NOT NULL,
        `riskarbiter_model_name_version` varchar(255) COLLATE utf8mb4_bin NOT NULL)
        """.trimMargin()
    )

    assertThat(declarativeSchemaMigrator.applyAll("test")).isEqualTo(MigrationStatus.Success)

    // Changed device_id column type
    resourceLoader.put(
      "$mainSource/name/space/table_1.sql", """
        CREATE TABLE `table_1` (
        `id` bigint(20) NOT NULL AUTO_INCREMENT,
        `lock_version` varchar(11) NOT NULL DEFAULT '0',
        `riskarbiter_model_name` varchar(255) COLLATE utf8mb4_bin NOT NULL,
        `riskarbiter_model_name_base` varchar(255) COLLATE utf8mb4_bin NOT NULL,
        `riskarbiter_model_name_version` varchar(255) COLLATE utf8mb4_bin NOT NULL)
        """.trimMargin()
    )

    assertFailsWith<IllegalStateException>(message = "Error: Column \"lock_version\" for table table_1 has incorrect type in the database.") {
      declarativeSchemaMigrator.requireAll()
    }
  }

  @Test
  fun skipsValidationForExcludedTables() {
    val mainSource = config.migrations_resources!![0]

    resourceLoader.put(
      "$mainSource/t1.sql", """
        CREATE TABLE excluded_table (id bigint, PRIMARY KEY (id))
        """.trimMargin()
    )

    assertThat(declarativeSchemaMigrator.requireAll()).isEqualTo(MigrationStatus.Success)
  }
}
