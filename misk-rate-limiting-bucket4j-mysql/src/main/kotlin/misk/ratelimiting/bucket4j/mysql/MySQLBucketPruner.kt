package misk.ratelimiting.bucket4j.mysql

import io.micrometer.core.instrument.MeterRegistry
import misk.logging.getLogger
import wisp.ratelimiting.RateLimitPrunerMetrics
import wisp.ratelimiting.bucket4j.Bucket4jPruner
import wisp.ratelimiting.bucket4j.ClockTimeMeter
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Clock
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

class MySQLBucketPruner @JvmOverloads constructor(
  clock: Clock,
  private val dataSource: DataSource,
  private val idColumn: String,
  meterRegistry: MeterRegistry,
  private val stateColumn: String,
  tableName: String,
  isMySQL8: Boolean = false,
  pageSize: Long = 1000
) : Bucket4jPruner() {
  override val clockTimeMeter = ClockTimeMeter(clock)

  private val deleteStatement = """
    DELETE FROM $tableName
    WHERE $idColumn = ?
  """.trimIndent()

  private val lockingClause = if (isMySQL8) {
    "FOR UPDATE SKIP LOCKED"
  } else {
    "FOR UPDATE"
  }
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
    $lockingClause
  """.trimIndent()

  private val prunerMetrics = RateLimitPrunerMetrics(meterRegistry)

  /**
   * Prunes the rate limit table, returning the number of rows pruned.
   */
  override fun prune() {
    val connection = dataSource.connection
    try {
      connection.autoCommit = false
      val millisTaken = measureTimeMillis {
        pruneLoop(connection)
      }
      prunerMetrics.pruningDuration.record(millisTaken.toDouble())
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

      var deletedBuckets = 0
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
          deletedBuckets++
        }

        // Advance result set cursor, breaking if we're out of rows
        if (isResultSetEmpty(resultSet)) {
          // Set cursor to the last read key
          cursor = key
          break
        }
      }

      connection.commit()
      prunerMetrics.bucketsPruned.increment(deletedBuckets.toDouble())
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

  companion object {
    private val logger = getLogger<MySQLBucketPruner>()
  }
}
