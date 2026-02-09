package misk.web.metadata.database

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import misk.hotwire.buildHtml
import misk.jdbc.AdminDatabaseEntry
import misk.tailwind.components.AlertError
import misk.tailwind.components.AlertSuccess
import misk.turbo.turbo_frame
import misk.web.FormValue
import misk.web.Post
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes

@Singleton
internal class DatabaseQueryAction @Inject constructor(private val databases: List<AdminDatabaseEntry>) : WebAction {
  @Post(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun post(@FormValue form: QueryForm): String = buildHtml {
    turbo_frame(id = "query-results") {
      val entry = databases.firstOrNull { it.name == form.db }
      if (entry == null) {
        AlertError("Database '${form.db}' not found.")
        return@turbo_frame
      }

      val sql = form.query.trim()
      if (sql.isEmpty()) {
        AlertError("Please enter a SQL query.")
        return@turbo_frame
      }

      val validationError = validateSql(sql)
      if (validationError != null) {
        AlertError(validationError)
        return@turbo_frame
      }

      try {
        val result = executeReadOnlyQuery(entry, sql)

        // Success
        AlertSuccess(
          "Query executed successfully. ${result.rows.size}${if (result.truncated) "+" else ""} rows returned."
        )

        if (result.columns.isNotEmpty()) {
          div("overflow-x-auto border border-gray-200 rounded-lg mt-2") {
            table("min-w-full divide-y divide-gray-200") {
              thead("bg-gray-50") {
                tr {
                  result.columns.forEach { col ->
                    th("px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider") { +col }
                  }
                }
              }
              tbody("bg-white divide-y divide-gray-200") {
                if (result.rows.isEmpty()) {
                  tr {
                    td("px-4 py-3 text-sm text-gray-500 italic") {
                      colSpan = result.columns.size.toString()
                      +"No rows returned."
                    }
                  }
                } else {
                  result.rows.forEach { row ->
                    tr("hover:bg-gray-50") {
                      row.forEach { cell ->
                        td("px-4 py-2 text-sm text-gray-900 font-mono whitespace-nowrap") { +(cell ?: "NULL") }
                      }
                    }
                  }
                }
              }
            }
          }

          if (result.truncated) {
            p("mt-2 text-sm text-gray-500") { +"Results truncated to $MAX_ROWS rows." }
          }
        }
      } catch (e: Exception) {
        AlertError("Query failed: ${e.message}")
      }
    }
  }

  data class QueryForm(val db: String, val query: String)

  data class QueryResult(val columns: List<String>, val rows: List<List<String?>>, val truncated: Boolean)

  companion object {
    const val PATH = "/_admin/database-beta/query"
    const val MAX_ROWS = 1000
    const val QUERY_TIMEOUT_SECONDS = 30

    private val ALLOWED_PREFIXES = listOf("SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN")
    private val FORBIDDEN_KEYWORDS =
      listOf(
        "INSERT",
        "UPDATE",
        "DELETE",
        "DROP",
        "ALTER",
        "CREATE",
        "TRUNCATE",
        "GRANT",
        "REVOKE",
        "RENAME",
        "REPLACE",
        "MERGE",
        "CALL",
        "EXEC",
      )

    fun validateSql(sql: String): String? {
      val trimmed = sql.trim()
      if (trimmed.isEmpty()) return "Query cannot be empty."

      val upper = trimmed.uppercase()

      // Check allowed prefixes
      if (ALLOWED_PREFIXES.none { upper.startsWith(it) }) {
        return "Only SELECT, SHOW, DESCRIBE, and EXPLAIN queries are allowed. Got: ${upper.split("\\s+".toRegex()).firstOrNull() ?: "empty"}"
      }

      // Check for multiple statements (semicolons not at the end)
      val withoutTrailingSemicolon = trimmed.trimEnd(';').trim()
      if (withoutTrailingSemicolon.contains(';')) {
        return "Multiple statements are not allowed. Please submit one query at a time."
      }

      // Check for forbidden keywords that might appear as sub-statements (e.g., SELECT ... INTO)
      val words = upper.split("\\s+".toRegex())
      if (words.contains("INTO") && words.first() == "SELECT") {
        return "SELECT INTO is not allowed. Only read-only queries are permitted."
      }

      return null
    }
  }

  private fun executeReadOnlyQuery(entry: AdminDatabaseEntry, sql: String): QueryResult {
    return entry.dataSource.connection.use { conn ->
      conn.isReadOnly = true
      conn.autoCommit = false
      try {
        val stmt = conn.createStatement()
        stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
        stmt.maxRows = MAX_ROWS + 1

        val rs = stmt.executeQuery(sql)
        val meta = rs.metaData
        val columnCount = meta.columnCount
        val columns = (1..columnCount).map { meta.getColumnLabel(it) }

        val rows = mutableListOf<List<String?>>()
        while (rs.next() && rows.size <= MAX_ROWS) {
          val row = (1..columnCount).map { rs.getString(it) }
          rows.add(row)
        }

        val truncated = rows.size > MAX_ROWS
        if (truncated) {
          rows.removeAt(rows.size - 1)
        }

        rs.close()
        stmt.close()

        QueryResult(columns = columns, rows = rows, truncated = truncated)
      } finally {
        // Always rollback to ensure no changes, even if somehow a write slipped through
        try {
          conn.rollback()
        } catch (_: Exception) {}
      }
    }
  }
}
