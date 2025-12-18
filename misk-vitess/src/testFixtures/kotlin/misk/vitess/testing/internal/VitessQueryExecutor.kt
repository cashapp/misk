package misk.vitess.testing.internal

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.sql.Statement
import misk.vitess.testing.VitessTable
import misk.vitess.testing.VitessTableType
import misk.vitess.testing.VitessTestDbException

/** VitessQueryExecutor exposes methods for querying against the VTGate on its exposed port. */
internal class VitessQueryExecutor(
  val hostname: String,
  val vtgatePort: Int,
  val vtgateUser: String,
  val vtgateUserPassword: String,
) {
  init {
    try {
      getVtgateConnection()
    } catch (e: Exception) {
      throw VitessQueryExecutorException("Failed to get vtgate connection on port ${vtgatePort}: ${e.message}", e)
    }
  }

  fun executeQuery(query: String, target: String = "@primary"): List<Map<String, Any>> {
    val connection = getVtgateConnection()
    return connection.use { conn -> executeQuery(conn, query, target) }
  }

  fun executeUpdate(query: String, target: String = "@primary"): Int {
    val connection = getVtgateConnection()
    return connection.use { conn -> executeUpdate(conn, query, target) }
  }

  /** Execute update with retry logic to handle retryable failures. */
  fun executeUpdateWithRetries(query: String, target: String = "@primary"): Int {
    var lastException: Exception? = null
    val maxRetries = 3
    val retryDelayMs = 500L

    val retryableErrorMessages = listOf("Keyspace does not have exactly one shard")

    for (attempt in 1..maxRetries) {
      try {
        return executeUpdate(query, target)
      } catch (e: Exception) {
        lastException = e

        // Check if this is a retryable exception type or message
        val isRetryableException =
          e.cause is SQLRecoverableException ||
            e.cause is SQLTransientException ||
            e is SQLRecoverableException ||
            e is SQLTransientException

        val isRetryableMessage =
          retryableErrorMessages.any { errorMessage -> e.message?.contains(errorMessage, ignoreCase = true) == true }

        if ((isRetryableException || isRetryableMessage) && attempt < maxRetries) {
          Thread.sleep(retryDelayMs * attempt)
        } else {
          throw e // Re-throw if not a retry-able error or max retries reached
        }
      }
    }

    // If we get here, all retries failed
    throw VitessQueryExecutorException(
      "Failed to executeUpdate after `$maxRetries` attempts. Query: `$query`, Last error: `${lastException?.message}`",
      lastException,
    )
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
    val url = "jdbc:mysql://${hostname}:${vtgatePort}/@primary?allowMultiQueries=true"
    return DriverManager.getConnection(url, vtgateUser, vtgateUserPassword)
  }
}

class VitessQueryExecutorException(message: String, cause: Throwable? = null) : VitessTestDbException(message, cause)
