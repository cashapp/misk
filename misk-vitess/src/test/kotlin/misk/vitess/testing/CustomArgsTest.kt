package misk.vitess.testing

import misk.vitess.testing.internal.VitessClusterConfig
import misk.vitess.testing.internal.VitessQueryExecutor
import misk.vitess.testing.internal.VitessQueryExecutorException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.io.path.pathString

class CustomArgsTest {
  companion object {
    private lateinit var testDb1: VitessTestDb
    private lateinit var testDb2: VitessTestDb
    private lateinit var testDb1RunResult: VitessTestDbStartupResult
    private lateinit var testDb2RunResult: VitessTestDbStartupResult
    private lateinit var testDb1QueryExecutor: VitessQueryExecutor
    private lateinit var testDb2QueryExecutor: VitessQueryExecutor

    // testDb1 args
    private const val DB1_CONTAINER_NAME = "custom_args_test_vitess_db"
    private const val DB1_PORT = 33003
    private const val DB1_MYSQL_VERSION = "8.0.42"
    private const val DB1_SQL_MODE = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
    private val DB1_TXN_ISO_LEVEL = TransactionIsolationLevel.READ_COMMITTED

    // testDb2 args
    private const val DB2_CONTAINER_NAME = "custom_args2_test_vitess_db"
    private const val DB2_PORT = 34003

    private val executorService = Executors.newFixedThreadPool(2)

    @JvmStatic
    @BeforeAll
    fun setup() {
      testDb1 = VitessTestDb(
        autoApplySchemaChanges = true,
        containerName = DB1_CONTAINER_NAME,
        enableScatters = false,
        enableDeclarativeSchemaChanges = true,
        port = DB1_PORT,
        keepAlive = true,
        mysqlVersion = DB1_MYSQL_VERSION,
        schemaDir = "filesystem:${Paths.get(System.getProperty("user.dir"), "src/test/resources/vitess/schema")}",
        sqlMode = DB1_SQL_MODE,
        transactionIsolationLevel = DB1_TXN_ISO_LEVEL,
        transactionTimeoutSeconds = Duration.ofSeconds(5),
        vitessImage = "vitess/vttestserver:v20.0.6-mysql80",
        vitessVersion = 20)

      testDb2 = VitessTestDb(
        autoApplySchemaChanges = false,
        containerName = DB2_CONTAINER_NAME,
        port = DB2_PORT,
        keepAlive = false)

      // Containers should be able to be run in parallel
      val future1:  Future<VitessTestDbStartupResult> = executorService.submit<VitessTestDbStartupResult> { testDb1.run() }
      val future2: Future<VitessTestDbStartupResult> = executorService.submit<VitessTestDbStartupResult> { testDb2.run() }
      testDb1RunResult = future1.get()
      testDb2RunResult = future2.get()

      testDb1QueryExecutor = VitessQueryExecutor(VitessClusterConfig(DB1_PORT))
      testDb2QueryExecutor = VitessQueryExecutor(VitessClusterConfig(DB2_PORT))
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      testDb1.shutdown()
      testDb2.shutdown()
    }
  }

  @Test
  fun `test user defined MySql version`() {
    val results = testDb1QueryExecutor.executeQuery("SELECT @@global.version;")
    val actualMysqlVersion = results[0]["@@global.version"]
    assertEquals("$DB1_MYSQL_VERSION-Vitess", actualMysqlVersion)
  }

  @Test
  fun `test user defined SQL mode`() {
    val results = testDb1QueryExecutor.executeQuery("SELECT @@global.sql_mode;")
    val actualSqlMode = results[0]["@@global.sql_mode"]
    assertEquals(DB1_SQL_MODE, actualSqlMode)
  }

  @Test
  fun `test user defined transaction isolation level`() {
    val results = testDb1QueryExecutor.executeQuery("SELECT @@global.transaction_ISOLATION;")
    val actualTransactionIsolationLevel = results[0]["@@global.transaction_ISOLATION"]
    assertEquals(DB1_TXN_ISO_LEVEL.value, actualTransactionIsolationLevel)
  }

  @Test
  fun `test scatter queries fail`() {
    val scatterQuery = "SELECT * FROM customers;"
    val exception = assertThrows<VitessQueryExecutorException> { testDb1QueryExecutor.executeQuery(scatterQuery) }

    assertTrue(exception.cause?.message!!.contains("plan includes scatter, which is disallowed"))
  }

  @Test
  fun `test transaction timeout`() {
    val exception = assertThrows<VitessQueryExecutorException> { testDb1QueryExecutor.executeTransaction("SELECT SLEEP(6);") }
    val actualMessage = exception.cause?.message!!
    val expectedMessageSubstring = "maximum statement execution time exceeded"
    assertTrue(
      actualMessage.contains(expectedMessageSubstring),
      "Expected message to contain \"$expectedMessageSubstring\" but was \"$actualMessage\"",
    )
  }

  @Test
  fun `test disabling scatters fails on an unsupported version`() {
    val exception = assertThrows<RuntimeException> { createUnsupportedNoScatterDb().run() }
    assertEquals(
      "Vitess image version must be >= 20 when scatters are disabled, found 19.",
      exception.message,
    )
  }

  @Test
  fun `test declarative schema changes are auto applied`() {
    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(DB1_PORT))
    val keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    val unshardedTables = vitessQueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = vitessQueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  @Test
  fun `test applySchema after run with keepAlive = false`() {
    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(DB2_PORT))
    // Keyspaces are still applied even if autoApplySchemaChanges is false.
    var keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    // However tables should not yet be applied.
    assertTablesNotApplied()

    // Truncating should work without needing to create a temporary schema.
    testDb2.truncate()

    var schemaDirPath = Paths.get("/tmp/vitess-test-db/${testDb2RunResult.containerId}/schema")
    assertFalse(Files.exists(schemaDirPath), "Schema directory ${schemaDirPath.pathString} should not exist")

    var applySchemaResult = testDb2.applySchema()
    // Now /tmp/{container_id}/schema should exist.
    assertTrue(Files.exists(schemaDirPath), "Schema directory ${schemaDirPath.pathString} should exist")

    assertTraditionalSchemaUpdatesApplied(applySchemaResult)
    assertTablesApplied()

    // Now reinitialize testDb2, with enableDeclarativeSchemaChanges set.
    testDb2 = VitessTestDb(
      autoApplySchemaChanges = false,
      containerName = DB2_CONTAINER_NAME,
      enableDeclarativeSchemaChanges = true,
      port = DB2_PORT,
      keepAlive = false)

    // This will start a new container since keepAlive is set to false.
    testDb2.run()

    // Now validate declarative schema changes are applied.
    assertTablesNotApplied()
    applySchemaResult = testDb2.applySchema()
    assertDeclarativeSchemaUpdatesApplied(applySchemaResult)
    assertTablesApplied()
  }

  @Test
  fun `test unsupported schema directory prefix `() {
    val exception = assertThrows<VitessTestDbStartupException> { createUnsupportedSchemaDirectoryDb().applySchema() }
    assertEquals(
      "Schema directory `some/path/without/filesystem/or/classpath` must start with one of the supported prefixes: [classpath:, filesystem:]",
      exception.message,
    )
  }

  private fun assertTraditionalSchemaUpdatesApplied(applySchemaResult: ApplySchemaResult) {
    // The vschema is always applied for each keyspace.
    assertEquals(2, applySchemaResult.vschemaUpdates.size)
    // In traditional schema changes, the DDL's are processed as is per .sql file.
    assertEquals(4, applySchemaResult.ddlUpdates.size)
  }

  private fun assertDeclarativeSchemaUpdatesApplied(applySchemaResult: ApplySchemaResult) {
    // The vschema is always applied for each keyspace.
    assertEquals(2, applySchemaResult.vschemaUpdates.size)
    // In declarative schema changes, the DDL's get consolidated as one diff per keyspace.
    assertEquals(2, applySchemaResult.ddlUpdates.size)
  }

  private fun assertTablesNotApplied() {
    assertEquals(testDb2QueryExecutor.getTables("gameworld").size, 0)
    assertEquals(testDb2QueryExecutor.getTables("gameworld_sharded").size, 0)
  }

  private fun assertTablesApplied() {
    val unshardedTables = testDb2QueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = testDb2QueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  private fun createUnsupportedNoScatterDb(): VitessTestDb {
    return VitessTestDb(
      containerName = "unsupported_scatter_vitess_db",
      enableScatters = false,
      port = DB1_PORT,
      vitessImage = "vitess/vttestserver:v19.0.9-mysql80")
  }

  private fun createUnsupportedSchemaDirectoryDb(): VitessTestDb {
    return VitessTestDb(
      containerName = "unsupported_schema_dir_vitess_db",
      schemaDir = "some/path/without/filesystem/or/classpath")
  }
}
