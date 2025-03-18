package misk.vitess.testing

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class VitessTestDbTest {

  private var url = "jdbc:mysql://localhost:27003/@primary"
  private var user = "root"
  private var password = ""

  @Test
  fun `test querying the database`() {
    val vitessTestDb = VitessTestDb()
    assertDoesNotThrow(vitessTestDb::run)
    val resultSet: ResultSet = executeQuery("SHOW KEYSPACES;")
    var rowCount = 0
    while (resultSet.next()) {
      rowCount++
    }

    assertEquals(2, rowCount)
  }

  @Test
  fun `test truncate fails when database is not running`() {
    val nonRunningDb = VitessTestDb(containerName = "non_running_vitess_test_db", port = 50003)

    val exception = assertThrows<VitessTestDbTruncateException>(nonRunningDb::truncate)
    assertEquals("Failed to truncate tables", exception.message)
    assertTrue(exception.cause!!.message!!.contains("Failed to get vtgate connection on port 50003"))
  }

  @Test
  fun `test truncate succeeds`() {
    val vitessTestDb = VitessTestDb()
    vitessTestDb.run()

    val insertCustomersSql =
      """
        INSERT INTO customers (email, token)
        VALUES ("jack@xyz.com", "test-token");
    """
        .trimIndent()

    val insertCustomersResultCount = executeUpdate(insertCustomersSql)
    assertEquals(1, insertCustomersResultCount)

    val insertGamesSql =
      """
        INSERT INTO games (title)
        VALUES ("Catan");
    """
        .trimIndent()

    val insertGamesResultCount = executeUpdate(insertGamesSql)
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
    VitessTestDb().run()
    val resultSet: ResultSet = executeQuery("SHOW VITESS_REPLICATION_STATUS;")
    val shardReplicaMap = mutableMapOf<String, Int>()
    val shardRdonlyMap = mutableMapOf<String, Int>()

    while (resultSet.next()) {
      val keyspace = resultSet.getString("Keyspace")
      val shard = resultSet.getString("Shard")
      val tabletType = resultSet.getString("TabletType")

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
    VitessTestDb().run()
    val customersSeqRowCount = getRowCount("customers_seq")
    assertEquals(1, customersSeqRowCount)

    val gamesSeqRowCount = getRowCount("games_seq")
    assertEquals(1, gamesSeqRowCount)
  }

  @Test
  fun `test default timezone set to UTC`() {
    VitessTestDb().run()
    val resultSet: ResultSet = executeQuery("SELECT @@global.time_zone;")

    if (resultSet.next()) {
      val actualTimeZone = resultSet.getString(1)
      assertEquals("+00:00", actualTimeZone)
    } else {
      Assertions.fail("Failed to get the time zone.")
    }
  }

  private fun executeQuery(query: String): ResultSet {
    val connection: Connection = DriverManager.getConnection(url, user, password)
    val statement: Statement = connection.createStatement()
    return statement.executeQuery(query)
  }

  private fun executeUpdate(query: String): Int {
    val connection: Connection = DriverManager.getConnection(url, user, password)
    val statement: Statement = connection.createStatement()
    return statement.executeUpdate(query)
  }

  private fun getRowCount(table: String): Int {
    val query = "SELECT COUNT(*) FROM $table;"
    val connection: Connection = DriverManager.getConnection(url, user, password)
    val statement: Statement = connection.createStatement()
    val resultSet: ResultSet = statement.executeQuery(query)
    if (resultSet.next()) {
      return resultSet.getInt(1)
    }
    throw RuntimeException("Failed to get row count.")
  }
}
