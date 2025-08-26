package misk.jdbc

import misk.resources.ResourceLoader
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.create.table.CreateTable
import misk.logging.getLogger
import java.sql.ResultSet
import java.util.regex.Pattern

internal class DeclarativeSchemaMigrator(
  private val resourceLoader: ResourceLoader,
  private val dataSourceService: DataSourceService,
  private val connector: DataSourceConnector,
  private val skeemaWrapper: SkeemaWrapper,
) : BaseSchemaMigrator(resourceLoader, dataSourceService) {
  private val logger = getLogger<DeclarativeSchemaMigrator>()

  override fun validateMigrationFile(migrationFile: MigrationFile): Boolean {
    return !Pattern.compile(connector.config().migrations_resources_regex)
      .matcher(migrationFile.filename).matches()
  }

  override fun applyAll(author: String): MigrationStatus {
    if (connector.config().type.isVitess) {
      // VitessTestDb handles applying declarative schema changes.
      throw UnsupportedOperationException("Declarative schema changes `applyAll()` is not supported for Vitess in Misk.")
    }
    var appliedMigrations = false
    val migrationFiles = getMigrationFiles()
    if (migrationFiles.isNotEmpty()) {
      skeemaWrapper.applyMigrations(migrationFiles)
      appliedMigrations = true
    }

    return if (appliedMigrations) MigrationStatus.Success else MigrationStatus.Empty
  }

  override fun requireAll(): MigrationStatus {
    if (connector.config().type.isVitess) {
      // TODO(aparajon): evaluate if we want to support this, as this can theoretically play nicely with VitessTestDb.
      throw UnsupportedOperationException("Declarative schema changes `requireAll()` is not currently supported for Vitess in Misk.")
    }

    val expectedTables = availableMigrations()
    val actualTables = appliedMigrations()
    val excludedTables = excludedTables()
    compareMigrations(expectedTables, actualTables, excludedTables)

    return MigrationStatus.Success
  }

  private fun excludedTables(): Set<String> {
    return connector.config().declarative_schema_config?.excluded_tables?.toSet() ?: emptySet()
  }

  /**
   * Compare expected tables from migration files to actual database tables
   */
  private fun compareMigrations(
    expectedTables: Map<String, Set<String>>,
    actualTables: Map<String, Set<String>>,
    excludedTables: Set<String>
  ) {
    logger.info { "Comparing expected tables $expectedTables to actual tables $actualTables in the database" }
    for ((expectedTable, expectedColumns) in expectedTables) {
      if (excludedTables.contains(expectedTable)) {
        continue
      }
      // Check if table exists in the database
      val actualColumns = actualTables[expectedTable]
        ?: throw IllegalStateException("Error: Table $expectedTable missing in the database.")

      // Compare columns in the migration file to actual columns in the database
      for (columnName in expectedColumns) {
        if (!actualColumns.contains(columnName)) {
          throw IllegalStateException("Error: Column $columnName for table $expectedTable is missing in the database.")
        }
        }
      }
    }

  /**
   * Helper function to parse migration files and extract expected tables and columns
   */
  private fun availableMigrations(): Map<String, Set<String>> {
    // Read the .sql Files
    val migrationFiles = getMigrationFiles()
    val tables = mutableMapOf<String, Set<String>>()

    for (file in migrationFiles) {
      val fileContent = resourceLoader.utf8(file.filename).toString()

      try {
        val normalizedContent = removeMySqlTableOptions(fileContent)

        // Parse the file content
        val statement = CCJSqlParserUtil.parse(normalizedContent)

        // Check if the parsed statement is a CREATE TABLE statement
        if (statement is CreateTable) {
          val tableName = statement.table.name.lowercase().removeSurrounding("`")
          val columns = mutableSetOf<String>()

          // Iterate over columns to extract names and data types
          statement.columnDefinitions?.forEach { columnDefinition ->
            columns.add(columnDefinition.columnName.lowercase().removeSurrounding("`"))
          }

          tables[tableName] = columns
        } else {
          throw IllegalStateException("No valid CREATE TABLE statement found in ${file.filename}")
        }
      } catch (e: Exception) {
        throw IllegalStateException("Failed to parse SQL in ${file.filename}", e)
      }
    }

    return tables
  }

  /**
   * Helper function to parse database and extract actual tables and columns
   */
  private fun appliedMigrations(): Map<String, Set<String>> {
    // Store actual database tables and their columns
    val actualTables = mutableMapOf<String, Set<String>>()
    val dbName = connector.config().database

    dataSourceService.dataSource.connection.use {
      conn ->
        // Use DatabaseMetaData to get all table names
        val metaData = conn.metaData
        val tablesResultSet: ResultSet =
          metaData.getTables(dbName, null, "%", arrayOf("TABLE"))

        // Iterate through all tables in the database
        while (tablesResultSet.next()) {
          val tableName = tablesResultSet.getString("TABLE_NAME").lowercase()

          // For each table, get column names and types
          val actualColumns = mutableSetOf<String>()
          val columnsResultSet: ResultSet =
            metaData.getColumns(dbName, null, tableName, "%")
          while (columnsResultSet.next()) {
            actualColumns.add(columnsResultSet.getString("COLUMN_NAME").lowercase())
          }

          // Close resources for this table
          columnsResultSet.close()
          actualTables[tableName] = actualColumns
        }

        tablesResultSet.close()
    }

    return actualTables
  }

  /**
   * Remove MySQL table options from CREATE statements to work around JSQLParser limitations,
   * since we only use JSQLParser to extract table and column names.
   */
  private fun removeMySqlTableOptions(sql: String): String {
    return sql.replace(
      Regex("\\)(\\s*ENGINE|\\s*DEFAULT|\\s*CHARSET|\\s*COLLATE|\\s*COMMENT|\\s*ROW_FORMAT|\\s*AUTO_INCREMENT|\\s*PARTITION).*?;",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
      ");"
    )
  }
}
