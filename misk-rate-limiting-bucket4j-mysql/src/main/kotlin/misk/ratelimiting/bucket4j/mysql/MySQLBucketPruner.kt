package misk.ratelimiting.bucket4j.mysql

import io.github.bucket4j.distributed.remote.RemoteBucketState
import io.github.bucket4j.distributed.serialization.DataOutputSerializationAdapter
import wisp.logging.getLogger
import wisp.ratelimiting.RateLimitPruner
import wisp.ratelimiting.bucket4j.ClockTimeMeter
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Clock
import javax.sql.DataSource

class MySQLBucketPruner @JvmOverloads constructor(
  clock: Clock,
  private val dataSource: DataSource,
  private val idColumn: String,
  private val stateColumn: String,
  tableName: String,
  pageSize: Long = 1000
) : RateLimitPruner {
  private val clockTimeMeter = ClockTimeMeter(clock)
  private val deleteStatement = """
    DELETE FROM $tableName
    WHERE $idColumn = ?
  """.trimIndent()

  /**
   * Notes about this query:
   * * We use `SKIP LOCKED` because that indicates bucket4j is currently using the row
   * * We use cursor based pagination because it's faster and because OFFSET based pagination
   * requires extra bookkeeping when deleting rows
   */
  private val pageStatement = """
    SELECT $idColumn, $stateColumn
    FROM $tableName
    WHERE $idColumn > ?
    ORDER BY $idColumn
    LIMIT $pageSize
    FOR UPDATE SKIP LOCKED
  """.trimIndent()

  /**
   * Prunes the rate limit table, returning the number of rows pruned.
   */
  override fun prune() {
    val connection = dataSource.connection
    try {
      connection.autoCommit = false
      pruneLoop(connection)
    } catch (e: Exception) {
      connection.rollback()
      logger.warn(e) { "Caught exception, rolling back current pruning transaction" }
    } finally {
      connection.close()
    }
  }

  private fun pruneLoop(connection: Connection) {
    var cursor = ""
    while (true) {
      val fetchPageStatement = connection.prepareStatement(pageStatement)
      fetchPageStatement.setString(1, cursor)
      val resultSet = fetchPageStatement.executeQuery()

      // Advance result set cursor, breaking if we're out of rows
      if (isResultSetEmpty(resultSet)) {
        break
      }

      while (true) {
        val key = resultSet.getString(idColumn)
        val stateBytes = resultSet.getBytes(stateColumn)
        val state = try {
          deserializeState(stateBytes)
        } catch (e: Exception) {
          logger.warn(e) { "Failed to deserialize state column for key $key" }
          continue
        }

        if (isBucketStale(state)) {
          val deleteBucketStatement = connection.prepareStatement(deleteStatement)
          deleteBucketStatement.setString(1, key)
          deleteBucketStatement.execute()
        }

        // Advance result set cursor, breaking if we're out of rows
        if (isResultSetEmpty(resultSet)) {
          // Set cursor to the last read key
          cursor = key
          break
        }
      }

      connection.commit()
    }
  }

  /**
   * Advances the result set, returning `true` if there were no more rows
   */
  private fun isResultSetEmpty(resultSet: ResultSet): Boolean {
    return try {
      !resultSet.next()
    } catch (_: SQLException) {
      // The docs for ResultSet.next() say that it can throw SQLException
      // if the result set is empty
      true
    }
  }

  private fun isBucketStale(state: RemoteBucketState): Boolean {
    val refillTimeNanos = state.calculateFullRefillingTime(clockTimeMeter.currentTimeNanos())
    return refillTimeNanos <= 0L
  }

  private fun deserializeState(bytes: ByteArray): RemoteBucketState {
    val inputStream = DataInputStream(ByteArrayInputStream(bytes))
    return RemoteBucketState.SERIALIZATION_HANDLE.deserialize(
      DataOutputSerializationAdapter.INSTANCE,
      inputStream
    )
  }

  companion object {
    private val logger = getLogger<MySQLBucketPruner>()
  }
}
