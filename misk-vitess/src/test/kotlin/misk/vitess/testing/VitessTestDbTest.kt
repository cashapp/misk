package misk.vitess.testing

import misk.vitess.testing.internal.VitessClusterConfig
import misk.vitess.testing.internal.VitessQueryExecutor
import misk.vitess.testing.internal.VitessQueryExecutorException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class VitessTestDbTest {
  companion object {
    private lateinit var vitessTestDb: VitessTestDb
    private lateinit var vitessTestDbRunResult: VitessTestDbStartupResult
    private lateinit var vitessQueryExecutor: VitessQueryExecutor

    @JvmStatic
    @BeforeAll
    fun setup() {
      vitessTestDb = VitessTestDb()
      vitessTestDbRunResult = vitessTestDb.run()
      vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(DefaultSettings.PORT))
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      val shutdownResult = vitessTestDb.shutdown()
      assertEquals(shutdownResult.containerId, vitessTestDbRunResult.containerId)
      assertEquals(shutdownResult.containerRemoved, true)
    }
  }

  @Test
  fun `test querying the database`() {
    val results = vitessQueryExecutor.executeQuery("SHOW KEYSPACES;")
    assertEquals(2, results.size)
  }

  @Test
  fun `test truncate succeeds`() {
    val insertCustomersSql =
      """
        INSERT INTO customers (email, token)
        VALUES ("jack@xyz.com", "test-token");
    """
        .trimIndent()

    val insertCustomersResultCount = vitessQueryExecutor.executeUpdate(insertCustomersSql)
    assertEquals(1, insertCustomersResultCount)

    val insertGamesSql =
      """
        INSERT INTO games (title)
        VALUES ("Catan");
    """
        .trimIndent()

    val insertGamesResultCount = vitessQueryExecutor.executeUpdate(insertGamesSql)
    assertEquals(1, insertGamesResultCount)

    assertDoesNotThrow(vitessTestDb::truncate)

    val customersRowCount = getRowCount("customers")
    assertEquals(0, customersRowCount)

    val gamesRowCount = getRowCount("games")
    assertEquals(0, gamesRowCount)

    // Assert sequence tables are not truncated
    val customersSeqRowCount = getRowCount("customers_seq")
    assertEquals(1, customersSeqRowCount)

    val gamesSeqRowCount = getRowCount("games_seq")
    assertEquals(1, gamesSeqRowCount)
  }

  @Test
  fun `test each shard has expected tablet types`() {
    val results = vitessQueryExecutor.executeQuery("SHOW VITESS_REPLICATION_STATUS;")
    val shardReplicaMap = mutableMapOf<String, Int>()
    val shardRdonlyMap = mutableMapOf<String, Int>()

    for (result in results) {
      val keyspace = result["Keyspace"]
      val shard = result["Shard"]
      val tabletType = result["TabletType"]

      if (tabletType == "REPLICA") {
        shardReplicaMap["$keyspace:$shard"] = shardReplicaMap.getOrDefault("$keyspace:$shard", 0) + 1
      } else if (tabletType == "RDONLY") {
        shardRdonlyMap["$keyspace:$shard"] = shardRdonlyMap.getOrDefault("$keyspace:$shard", 0) + 1
      }
    }

    for ((shard, replicaCount) in shardReplicaMap) {
      assertEquals(2, replicaCount, "Shard $shard has replica tablet count of $replicaCount.")
    }

    for ((shard, rdonlyCount) in shardRdonlyMap) {
      assertEquals(0, rdonlyCount, "Shard $shard has a read-only tablet count of $rdonlyCount.")
    }
  }

  @Test
  fun `test sequence tables are auto-initialized`() {
    val customersSeqRowCount = getRowCount("customers_seq")
    assertEquals(1, customersSeqRowCount)

    val gamesSeqRowCount = getRowCount("games_seq")
    assertEquals(1, gamesSeqRowCount)
  }

  @Test
  fun `test schema changes are auto-applied`() {
    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(27003))
    val keyspaces = vitessQueryExecutor.getKeyspaces()
    assertArrayEquals(arrayOf("gameworld", "gameworld_sharded"), keyspaces.toTypedArray())

    val unshardedTables = vitessQueryExecutor.getTables("gameworld").map { it.tableName }
    assertArrayEquals(arrayOf("customers_seq", "games_seq"), unshardedTables.toTypedArray())

    val shardedTables = vitessQueryExecutor.getTables("gameworld_sharded").map { it.tableName }
    assertArrayEquals(arrayOf("customers", "games"), shardedTables.toTypedArray())
  }

  @Test
  fun `test database stays alive when keepAlive is true and args are the same`() {
    val containerId = vitessTestDbRunResult.containerId

    // Now attempt to start a new instance with the same args
    val newRunResult = VitessTestDb().run()
    assertEquals(containerId, newRunResult.containerId)
  }

  @Test
  fun `test default timezone set to UTC`() {
    val results = vitessQueryExecutor.executeQuery("SELECT @@global.time_zone;")
    val actualTimeZone = results[0]["@@global.time_zone"]
    assertEquals("+00:00", actualTimeZone)
  }

  @Test
  fun `test default MySql version`() {
    val results = vitessQueryExecutor.executeQuery("SELECT @@global.version;")
    val actualMysqlVersion = results[0]["@@global.version"]
    assertEquals("${DefaultSettings.MYSQL_VERSION}-Vitess", actualMysqlVersion)
  }

  @Test
  fun `test default SQL mode`() {
    val results = vitessQueryExecutor.executeQuery("SELECT @@global.sql_mode;")
    val actualSqlMode = results[0]["@@global.sql_mode"]
    assertEquals(DefaultSettings.SQL_MODE, actualSqlMode)
  }

  @Test
  fun `test default transaction isolation level`() {
    val results = vitessQueryExecutor.executeQuery("SELECT @@global.transaction_ISOLATION;")
    val actualTransactionIsolationLevel = results[0]["@@global.transaction_ISOLATION"]
    assertEquals(DefaultSettings.TRANSACTION_ISOLATION_LEVEL.value, actualTransactionIsolationLevel)
  }

  @Test
  fun `explicit exception thrown on query errors`() {
    val executeQueryException =
      assertThrows<VitessQueryExecutorException> {
        vitessQueryExecutor.executeQuery("SELECT * FROM non_existent_table")
      }
    assertEquals("Failed to run executeQuery on query: SELECT * FROM non_existent_table", executeQueryException.message)

    val executeException =
      assertThrows<VitessQueryExecutorException> {
        vitessQueryExecutor.execute("UPDATE non_existent_table SET column = value WHERE id = 1")
      }
    assertEquals(
      "Failed to run execute on query: UPDATE non_existent_table SET column = value WHERE id = 1",
      executeException.message,
    )
  }

  private fun getRowCount(table: String): Int {
    val query = "SELECT COUNT(*) FROM $table;"
    val results = vitessQueryExecutor.executeQuery(query)
    return (results[0]["count(*)"] as Long).toInt()
  }
}
