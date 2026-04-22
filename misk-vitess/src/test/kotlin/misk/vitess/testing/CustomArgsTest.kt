package misk.vitess.testing

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.io.path.pathString
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.testing.internal.VitessQueryExecutorException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomArgsTest {
  companion object {
    private lateinit var testDb1: VitessTestDb
    private lateinit var testDb2: VitessTestDb
    private lateinit var testDb1RunResult: VitessTestDbStartupResult
    private lateinit var testDb2RunResult: VitessTestDbStartupResult

    // testDb1 args
    private const val DB1_CONTAINER_NAME = "custom_args_test_vitess_db"
    private const val DB1_MYSQL_VERSION = "8.0.42"
    private const val DB1_SQL_MODE =
      "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
    private val DB1_TXN_ISO_LEVEL = TransactionIsolationLevel.READ_COMMITTED
    private val DB1_TXN_MODE = TransactionMode.SINGLE

    // testDb2 args
    private const val DB2_CONTAINER_NAME = "custom_args2_test_vitess_db"

    private val executorService = Executors.newFixedThreadPool(2)

    @JvmStatic
    @BeforeAll
    fun setup() {
      testDb1 =
        VitessTestDb(
          autoApplySchemaChanges = true,
          containerName = DB1_CONTAINER_NAME,
          enableScatters = false,
          enableDeclarativeSchemaChanges = true,
          port = DefaultSettings.DYNAMIC_PORT,
          keepAlive = true,
          mysqlVersion = DB1_MYSQL_VERSION,
          schemaDir = "filesystem:${Paths.get(System.getProperty("user.dir"), "src/test/resources/vitess/schema")}",
          sqlMode = DB1_SQL_MODE,
          transactionIsolationLevel = DB1_TXN_ISO_LEVEL,
          transactionMode = DB1_TXN_MODE,
          transactionTimeoutSeconds = Duration.ofSeconds(5),
          vitessImage = "ghcr.io/block/vitess/vttestserver:23.0.3-block.1-mysql84",
          vitessVersion = 23,
        )

      testDb2 =
        VitessTestDb(
          autoApplySchemaChanges = false,
          containerName = DB2_CONTAINER_NAME,
          enableInMemoryStorage = true,
          inMemoryStorageSize = "1G",
          port = DefaultSettings.DYNAMIC_PORT,
          keepAlive = false,
        )

      // Containers should be able to be run in parallel
      val future1: Future<VitessTestDbStartupResult> =
        executorService.submit<VitessTestDbStartupResult> { testDb1.run() }
      val future2: Future<VitessTestDbStartupResult> =
        executorService.submit<VitessTestDbStartupResult> { testDb2.run() }
      testDb1RunResult = future1.get()
      testDb2RunResult = future2.get()
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
    val results = testDb1.executeQuery("SELECT version();")
    val actualMysqlVersion = results[0]["version()"]
    assertEquals("$DB1_MYSQL_VERSION-Vitess", actualMysqlVersion)
  }

  @Test
  fun `test user defined SQL mode`() {
    val results = testDb1.executeQuery("SELECT @@global.sql_mode;")
    val actualSqlMode = results[0]["@@global.sql_mode"]
    assertEquals(DB1_SQL_MODE, actualSqlMode)
  }

  @Test
  fun `test user defined transaction isolation level`() {
    val results = testDb1.executeQuery("SELECT @@global.transaction_ISOLATION;")
    val actualTransactionIsolationLevel = results[0]["@@global.transaction_ISOLATION"]
    assertEquals(DB1_TXN_ISO_LEVEL.value, actualTransactionIsolationLevel)
  }

  @Test
  fun `test scatter queries fail by default but succeed with query hint`() {
    testDb1.truncate()
    testDb1.executeUpdate("INSERT INTO customers (email, token) VALUES ('jack@xyz.com', 'token');")

    val exception = assertThrows<VitessQueryExecutorException> { testDb1.executeQuery("SELECT * FROM customers;") }
    assertTrue(exception.cause?.message!!.contains("plan includes scatter, which is disallowed"))

    val results = testDb1.executeQuery("SELECT /*vt+ ALLOW_SCATTER */ * FROM customers;")
    assertEquals(1, results.size)
  }

  @Test
  fun `test cross-shard writes fail with SINGLE transaction mode`() {
    testDb1.truncate()

    val keyspace = Keyspace("gameworld_sharded")
    val shard1 = Shard(keyspace, "-80")
    val shard2 = Shard(keyspace, "80-")

    // Insert rows one at a time using auto-increment sequences, then find two on different shards.
    for (i in 1..10) {
      testDb1.executeUpdate("INSERT INTO customers (email, token) VALUES ('user$i@xyz.com', 'token$i');")
    }

    val rows = testDb1.executeQuery("SELECT /*vt+ ALLOW_SCATTER */ id FROM customers;")
    val ids = rows.map { (it["id"] as Number).toLong() }

    // Partition IDs by shard and pick one from each.
    val byShard = ids.groupBy { id -> if (shard1.contains(Shard.Key.hash(id))) shard1 else shard2 }
    assertEquals(2, byShard.keys.size, "All IDs landed on the same shard: $ids")
    val idA = byShard[shard1]!!.first()
    val idB = byShard[shard2]!!.first()
    val exception = assertThrows<VitessQueryExecutorException> {
      testDb1.executeTransaction(
        "UPDATE customers SET token = 'updated' WHERE id = $idA;" +
          "UPDATE customers SET token = 'updated' WHERE id = $idB;"
      )
    }
    assertTrue(
      exception.cause?.message!!.contains("multi-db transaction attempted"),
      "Expected 'multi-db transaction attempted' but got: ${exception.cause?.message}",
    )
  }

  @Test
  fun `test transaction timeout`() {
    val exception = assertThrows<VitessQueryExecutorException> { testDb1.executeTransaction("SELECT SLEEP(6);") }
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
    assertEquals("Vitess image version must be >= 20 when scatters are disabled, found 19.", exception.message)
  }

  @Test
  fun `test declarative schema changes are auto applied`() {
    val keyspaces = testDb1.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    val unshardedTables = testDb1.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = testDb1.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  @Test
  fun `test applySchema after run with keepAlive = false`() {
    // Keyspaces are still applied even if autoApplySchemaChanges is false.
    var keyspaces = testDb2.getKeyspaces()
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
    testDb2 =
      VitessTestDb(
        autoApplySchemaChanges = false,
        containerName = DB2_CONTAINER_NAME,
        enableDeclarativeSchemaChanges = true,
        port = DefaultSettings.DYNAMIC_PORT,
        keepAlive = false,
      )

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

  @Test
  fun `test invalid inMemoryStorageSize`() {
    val exception =
      assertThrows<IllegalArgumentException> {
        VitessTestDb(
            containerName = "invalid_in_memory_storage_size_vitess_db",
            enableInMemoryStorage = true,
            inMemoryStorageSize = "100A",
          )
          .run()
      }
    assertEquals(
      "Invalid `inMemoryStorageSize`: `100A`. Must match pattern '\\d+[KMG]', e.g., '1G', '512M', or '1024K'.",
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
    assertEquals(testDb2.getTables("gameworld").size, 0)
    assertEquals(testDb2.getTables("gameworld_sharded").size, 0)
  }

  private fun assertTablesApplied() {
    val unshardedTables = testDb2.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = testDb2.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  private fun createUnsupportedNoScatterDb(): VitessTestDb {
    return VitessTestDb(
      containerName = "unsupported_scatter_vitess_db",
      enableScatters = false,
      port = DefaultSettings.DYNAMIC_PORT,
      vitessImage = "ghcr.io/block/vitess/vttestserver:mysql84",
      vitessVersion = 19,
    )
  }

  private fun createUnsupportedSchemaDirectoryDb(): VitessTestDb {
    return VitessTestDb(
      containerName = "unsupported_schema_dir_vitess_db",
      schemaDir = "some/path/without/filesystem/or/classpath",
    )
  }
}
