package misk.jdbc

import misk.backoff.FlatBackoff
import misk.backoff.retry
import mu.KotlinLogging
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLSyntaxErrorException
import java.sql.Timestamp
import java.time.Duration

object ScaleSafetyChecks {
  private val logger = KotlinLogging.logger {}
  private val COMMENT_PATTERN = "/\\*+[^*]*\\*+(?:[^/*][^*]*\\*+)*/".toRegex()
  private val DML = setOf("insert", "delete", "update")

  fun getLastLoggedCommand(connection: Connection): Timestamp? {
    return connection.createStatement().use { session ->
      session.executeQuery("SELECT MAX(event_time) FROM mysql.general_log")
        .map { it.getTimestamp(1) }
        .singleOrNull()
    }
  }

  fun checkQueryForTableScan(connection: Connection, database: String?, query: String) {
    if (isDml(query) || query.startsWith("EXPLAIN", true)) return

    val explanations = connection.createStatement().use { statement ->
      try {
        database?.let { statement.execute("USE `$it`") }
        retry(5, FlatBackoff(Duration.ofMillis(100))) {
          statement.executeQuery("EXPLAIN ${query.replace("\n", " ")}")
              .map { Explanation.fromResultSet(it) }
        }
      } catch (e: SQLSyntaxErrorException) {
        // TODO(jontirsen): This happens during multi threaded tests, let's ignore it for
        //   now. Implement proper support for multi-threading at some point (it's hard).
        logger.warn(e) { "Failed to check for table scans in \"$query\"" }
        null
      } catch (e: SQLException) {
        val message = e.message
        if (message != null) null
        else throw e
      }
    }

    explanations?.let {
      if (it.all { explanation -> explanation.isIndexed() }) {
        return@let
      }
      val plan = explanations.joinToString("\n")
      if (it.all { explanation -> explanation.isProbablyOkay(query) }) {
        logger.warn { "Possibly missing index. Investigate query plan.\n$query\nPlan is:$plan" }
      } else {
        throw TableScanException("Missing index on query:\n$query\nPlan is:\n$plan")
      }
    }
  }

  fun isDml(query: String): Boolean {
    val first = query
      .replace(COMMENT_PATTERN, "")
      .trimStart()
      .toLowerCase()
      .takeWhile { !it.isWhitespace() }
    return DML.contains(first)
  }

  /**
   * Digs into the MySQL log to find the last executed DML statement.
   */
  fun extractQueriesSince(connection: Connection, mysqlTime: Timestamp): List<String> {
    return connection.let { c ->
      c.prepareStatement("""
                  SELECT argument
                  FROM mysql.general_log
                  WHERE command_type in ('Query', 'Execute')
                  AND NOT argument LIKE '%general_log%'
                  AND NOT argument = 'begin'
                  AND NOT argument LIKE '%1 != 1%'
                  AND NOT lower(argument) LIKE '%information_schema%'
                  AND NOT lower(argument) LIKE 'use %'
                  AND NOT lower(argument) LIKE 'show %'
                  AND NOT lower(argument) LIKE 'describe %'
                  AND event_time > ?
                  ORDER BY event_time DESC
                """.trimIndent()).use { s ->
        s.setTimestamp(1, mysqlTime)
        s.executeQuery().map { it.getString(1) }
      }
    }
  }

  /**
   * Turn on MySQL general_log so that we can inspect it in the detectors.
   */
  fun turnOnSqlGeneralLogging(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute("SET GLOBAL log_output = 'TABLE'")
      statement.execute("SET GLOBAL general_log = 1")
    }
  }
}
