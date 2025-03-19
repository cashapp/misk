package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDbException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

/** VitessQueryExecutor exposes methods for querying against the VTGate. */
internal class VitessQueryExecutor(private val vitessClusterConfig: VitessClusterConfig) {
  init {
    try {
      getVtgateConnection()
    } catch (e: Exception) {
      throw VitessQueryExecutorException(
        "Failed to get vtgate connection on port ${vitessClusterConfig.vtgatePort}: ${e.message}",
        e,
      )
    }
  }

  fun executeQuery(query: String, target: String = "@primary"): List<Map<String, Any>> {
    val connection = getVtgateConnection()
    return connection.use { conn -> executeQuery(conn, query, target) }
  }

  fun execute(query: String, target: String = "@primary"): Boolean {
    val connection = getVtgateConnection()
    return connection.use { conn -> execute(conn, query, target) }
  }

  fun executeUpdate(query: String, target: String = "@primary"): Int {
    val connection = getVtgateConnection()
    return connection.use { conn -> executeUpdate(conn, query, target) }
  }

  fun executeTransaction(query: String, target: String = "@primary"): Boolean {
    val connection = getVtgateConnection()
    return connection.use { conn ->
      conn.autoCommit = false
      val result = execute(conn, query, target)
      conn.commit()
      result
    }
  }

  fun getKeyspaces(): List<String> {
    val result = executeQuery("SHOW KEYSPACES;")
    return result.map { it["Database"] as String }.sorted()
  }

  fun truncate() {
    val connection = getVtgateConnection()
    connection.use { conn -> truncate(conn) }
  }

  fun truncate(connection: Connection) {
    try {
      val shards = getShards(connection)
      shards.forEach { keyspace ->
        val tables = getTables(connection, keyspace)
        tables
          .filter { it.type != VitessTableType.SEQUENCE }
          .forEach { table ->
            while (true) {
              val rowsDeleted = executeUpdate(connection, "DELETE FROM ${table.tableName} LIMIT 10000;", keyspace)
              if (rowsDeleted == 0) {
                break
              }
            }
          }
      }
    } catch (e: Exception) {
      throw VitessQueryExecutorException("Failed to truncate tables", e)
    } finally {
      execute(connection, "USE @primary;")
    }
  }

  fun getTables(keyspace: String): List<VitessTable> {
    val connection = getVtgateConnection()
    return connection.use { conn -> getTables(conn, keyspace) }
  }

  private fun executeQuery(connection: Connection, query: String, target: String = "@primary"): List<Map<String, Any>> {
    try {
      connection.catalog = target
      val statement: Statement = connection.createStatement()
      val resultSet = statement.executeQuery(query)

      val result = mutableListOf<Map<String, Any>>()
      val metaData = resultSet.metaData
      val columnCount = metaData.columnCount

      while (resultSet.next()) {
        val row = mutableMapOf<String, Any>()
        // In JDBC, column indexing starts at 1.
        for (i in 1..columnCount) {
          row[metaData.getColumnName(i)] = resultSet.getObject(i) ?: ""
        }
        result.add(row)
      }

      resultSet.close()
      statement.close()
      return result
    } catch (e: Exception) {
      throw VitessQueryExecutorException("Failed to run executeQuery on query: $query", e)
    }
  }

  private fun execute(connection: Connection, query: String, target: String = "@primary"): Boolean {
    try {
      connection.catalog = target
      val statement: Statement = connection.createStatement()
      val result = statement.execute(query)
      statement.close()
      return result
    } catch (e: Exception) {
      throw VitessQueryExecutorException("Failed to run execute on query: $query", e)
    }
  }

  private fun executeUpdate(connection: Connection, query: String, target: String = "@primary"): Int {
    try {
      connection.catalog = target
      val statement: Statement = connection.createStatement()
      val result = statement.executeUpdate(query)
      statement.close()
      return result
    } catch (e: Exception) {
      throw VitessQueryExecutorException("Failed to run executeUpdate on query: $query", e)
    }
  }

  private fun getTables(connection: Connection, keyspaceOrShard: String): List<VitessTable> {
    val result = executeQuery(connection, "SHOW TABLE STATUS;", keyspaceOrShard)
    return result
      .map { row ->
        val tableName = row["Name"].toString()
        val comment = row["Comment"].toString()
        val type = if (comment.contains("vitess_sequence")) VitessTableType.SEQUENCE else VitessTableType.STANDARD
        VitessTable(tableName, type)
      }
      .sortedBy { it.tableName }
  }

  private fun getShards(connection: Connection): List<String> {
    val result = executeQuery(connection, "SHOW VITESS_SHARDS;")
    return result.map { it["Shards"] as String }.sorted()
  }

  private fun getVtgateConnection(): Connection {
    val url =
      "jdbc:mysql://${vitessClusterConfig.hostname}:${vitessClusterConfig.vtgatePort}/@primary?allowMultiQueries=true"
    return DriverManager.getConnection(url, vitessClusterConfig.vtgateUser, vitessClusterConfig.vtgateUserPassword)
  }
}

class VitessQueryExecutorException(message: String, cause: Throwable? = null) : VitessTestDbException(message, cause)
