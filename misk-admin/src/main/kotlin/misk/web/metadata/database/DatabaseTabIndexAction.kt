package misk.web.metadata.database

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.onChange
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import misk.jdbc.AdminDatabaseEntry
import misk.tailwind.components.AlertInfo
import misk.tailwind.components.AlertInfoHighlight
import misk.turbo.turbo_frame
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout

@Singleton
internal class DatabaseTabIndexAction
@Inject
constructor(private val dashboardPageLayout: DashboardPageLayout, private val databases: List<AdminDatabaseEntry>) :
  WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(@QueryParam db: String?, @QueryParam table: String?): String =
    dashboardPageLayout
      .newBuilder()
      .headBlock {
        script {
          type = "module"
          src = "/static/controllers/database_controller.js"
        }
      }
      .build { _, _, _ ->
        val selectedDb = databases.firstOrNull { it.name == db }
        val tableNames = selectedDb?.let { getTableNames(it) } ?: emptyList()
        val columns = if (selectedDb != null && table != null) getColumns(selectedDb, table) else emptyList()

        div("container mx-auto p-8") {
          h1("text-3xl font-bold mb-4") { +"Database" }
          AlertInfoHighlight("Execute read-only SQL queries against your database.")

          if (databases.isEmpty()) {
            AlertInfo("No databases registered. Install JdbcModule to automatically register databases.")
          } else {
            // Database selector
            DatabaseSelector(db)

            if (selectedDb != null) {
              div("grid grid-cols-4 gap-6 mt-6") {
                // Left column: Table list
                div("col-span-1") { TableList(db!!, tableNames, table) }

                // Right column: Schema + Query
                div("col-span-3") {
                  // Table schema
                  if (table != null && columns.isNotEmpty()) {
                    SchemaTable(table, columns)
                  }

                  // SQL Query form
                  QueryForm(db!!, table)

                  // Results area (turbo-frame, populated by POST response)
                  turbo_frame(id = "query-results") {}
                }
              }
            }
          }
        }
      }

  private fun TagConsumer<*>.DatabaseSelector(selectedDb: String?) {
    form {
      action = PATH
      method = FormMethod.get

      label("block text-sm font-medium text-gray-700") {
        htmlFor = "db"
        +"Database"
      }
      select(
        "mt-1 block w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6"
      ) {
        id = "db"
        name = "db"
        onChange = "this.form.submit()"

        option {
          value = ""
          +"Select a database..."
        }

        databases.forEach { entry ->
          option {
            if (selectedDb == entry.name) {
              selected = true
            }
            value = entry.name
            +entry.name
          }
        }
      }
    }
  }

  private fun TagConsumer<*>.TableList(db: String, tableNames: List<String>, selectedTable: String?) {
    h2("text-lg font-semibold text-gray-900 mb-3") { +"Tables" }
    if (tableNames.isEmpty()) {
      p("text-sm text-gray-500 italic") { +"No tables found." }
    } else {
      div("border border-gray-200 rounded-lg overflow-hidden") {
        tableNames.forEach { tableName ->
          val isSelected = tableName == selectedTable
          val bgClass = if (isSelected) "bg-blue-50 text-blue-700 font-medium" else "text-gray-700 hover:bg-gray-50"
          a(
            classes = "block px-3 py-2 text-sm border-b border-gray-100 last:border-b-0 $bgClass",
            href = "$PATH?db=$db&table=$tableName",
          ) {
            +tableName
          }
        }
      }
    }
  }

  private fun TagConsumer<*>.SchemaTable(tableName: String, columns: List<ColumnInfo>) {
    div("mb-6") {
      h3("text-md font-semibold text-gray-900 mb-2") {
        +"Schema: "
        span("font-mono text-blue-600") { +tableName }
      }
      div("overflow-x-auto border border-gray-200 rounded-lg") {
        table("min-w-full divide-y divide-gray-200") {
          thead("bg-gray-50") {
            tr {
              th(classes = "px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider") {
                +"Column"
              }
              th(classes = "px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider") { +"Type" }
              th(classes = "px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider") { +"Size" }
              th(classes = "px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider") {
                +"Nullable"
              }
            }
          }
          tbody("bg-white divide-y divide-gray-200") {
            columns.forEach { col ->
              tr {
                td("px-4 py-2 text-sm font-mono text-gray-900") { +col.name }
                td("px-4 py-2 text-sm text-gray-600") { +col.typeName }
                td("px-4 py-2 text-sm text-gray-600") { +col.size.toString() }
                td("px-4 py-2 text-sm text-gray-600") { +(if (col.nullable) "YES" else "NO") }
              }
            }
          }
        }
      }
    }
  }

  private fun TagConsumer<*>.QueryForm(db: String, selectedTable: String?) {
    div("mb-4") {
      h3("text-md font-semibold text-gray-900 mb-2") { +"SQL Query" }
      form {
        action = DatabaseQueryAction.PATH
        method = FormMethod.post
        attributes["data-turbo-frame"] = "query-results"

        input(type = InputType.hidden) {
          name = "db"
          value = db
        }

        textArea(
          classes =
            "block w-full rounded-md border-0 py-2 px-3 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 font-mono"
        ) {
          name = "query"
          id = "query"
          rows = "6"
          placeholder =
            if (selectedTable != null) "SELECT * FROM $selectedTable LIMIT 100"
            else "SELECT * FROM table_name LIMIT 100"
        }

        div("mt-3 flex items-center gap-4") {
          button(
            classes =
              "rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          ) {
            type = kotlinx.html.ButtonType.submit
            +"Run Query"
          }
          span("text-xs text-gray-500") { +"Read-only. Only SELECT, SHOW, DESCRIBE, and EXPLAIN queries are allowed." }
        }
      }
    }
  }

  private fun getTableNames(entry: AdminDatabaseEntry): List<String> =
    try {
      entry.dataSource.connection.use { conn ->
        val tables = mutableListOf<String>()
        val rs = conn.metaData.getTables(conn.catalog, conn.schema, "%", arrayOf("TABLE"))
        while (rs.next()) {
          tables.add(rs.getString("TABLE_NAME"))
        }
        rs.close()
        tables.sorted()
      }
    } catch (e: Exception) {
      emptyList()
    }

  private fun getColumns(entry: AdminDatabaseEntry, tableName: String): List<ColumnInfo> =
    try {
      entry.dataSource.connection.use { conn ->
        val columns = mutableListOf<ColumnInfo>()
        val rs = conn.metaData.getColumns(conn.catalog, conn.schema, tableName, "%")
        while (rs.next()) {
          columns.add(
            ColumnInfo(
              name = rs.getString("COLUMN_NAME"),
              typeName = rs.getString("TYPE_NAME"),
              size = rs.getInt("COLUMN_SIZE"),
              nullable = rs.getInt("NULLABLE") != 0,
            )
          )
        }
        rs.close()
        columns
      }
    } catch (e: Exception) {
      emptyList()
    }

  data class ColumnInfo(val name: String, val typeName: String, val size: Int, val nullable: Boolean)

  companion object {
    const val PATH = "/_admin/database-beta/"
  }
}
