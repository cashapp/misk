package misk.hibernate

import com.google.common.base.CaseFormat
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Column
import java.sql.Connection
import java.sql.DatabaseMetaData

/**
 * Reports the inconsistencies between two [Declaration]s. The DB schema must be able to carry the
 * Hibernate schema. For example, it is okay for Hibernate not to know about all of the database's tables,
 * but the converse is not true.
 */
internal class SchemaValidator {
  private val messages = mutableListOf<Message>()
  private var pathBreadcrumbs = mutableListOf<String>()

  /** Compares the Database's Schema against Hibernate's Schema and throws errors if there are problems. */
  internal fun validate(
    sessionFactory: SessionFactory,
    hibernateMetadata: Metadata,
    config: DataSourceConfig
  ) {
    // TODO: Figure out how to extract schema from a Vitess connection.
    if (config.type == DataSourceType.VITESS) return

    val dbMetadata = sessionFactory.doWork { metaData }
    val dbSchema = readDeclarationFromDatabase(dbMetadata, config.database)

    val hibernateSchema = readDeclarationFromHibernate(hibernateMetadata)

    withDeclaration(config.database!!) {
      validateDatabase(dbSchema, hibernateSchema)
    }
    throwIfErrorsFound()
  }

  private fun throwIfErrorsFound() {
    check(!messages.any { it.error }) {
      reportString()
    }
  }

  private fun readDeclarationFromDatabase(
    metaData: DatabaseMetaData,
    catalog: String?
  ): DatabaseDeclaration {
    val ignoreTables = arrayOf("schema_version")
    val tablesRs = metaData.getTables(catalog, null, "%", null)
    val schemaTables = mutableListOf<TableDeclaration>()

    while (tablesRs.next()) {
      val tableName = tablesRs.getString("TABLE_NAME")
      if (tableName in ignoreTables) continue
      val tableSchema: String? = tablesRs.getString("TABLE_SCHEM")
      val columnResultSet = metaData.getColumns(catalog, tableSchema, tableName, "%")
      val columns = mutableListOf<ColumnDeclaration>()

      while (columnResultSet.next()) {
        columns += ColumnDeclaration(
            columnResultSet.getString("COLUMN_NAME"),
            columnResultSet.getString("IS_NULLABLE") == "YES")
      }
      schemaTables += TableDeclaration(tableName, columns)
    }
    return DatabaseDeclaration("databaseSchema", schemaTables)
  }

  private fun readDeclarationFromHibernate(metadata: Metadata): DatabaseDeclaration {
    val hibernateTables = mutableListOf<TableDeclaration>()
    val tableIt = metadata.collectTableMappings().iterator()
    while (tableIt.hasNext()) {
      val table = tableIt.next()
      val tableName = table.name

      val columnsIt = table.columnIterator
      val columns = mutableListOf<ColumnDeclaration>()
      while (columnsIt.hasNext()) {
        val column = columnsIt.next() as Column
        columns += ColumnDeclaration(column.name, column.isNullable)
      }

      hibernateTables += TableDeclaration(tableName, columns)
    }
    return DatabaseDeclaration("hibernateSchema", hibernateTables)
  }

  private fun reportString(): String {
    val errorReport = StringBuilder("Failed Schema Validation: \n\n")
    for (message in messages) {
      if (message.error) errorReport.append("ERROR ") else errorReport.append("WARNING ")
      errorReport.append("at ${message.path.joinToString(separator = ".")}:\n")
      errorReport.append("  ${message.text.replace("\n", "\n  ")}\n")
      errorReport.append("\n")
    }
    return errorReport.toString()
  }

  private fun validateDatabase(dbSchema: DatabaseDeclaration, hibernateSchema: DatabaseDeclaration) {
    val (dbOnly, hibernateOnly, intersectionPairs) = splitChildren(
        dbSchema.tables, hibernateSchema.tables)

    validate(hibernateOnly.isEmpty()) {
      "Database missing tables ${hibernateOnly.map { it.name }}"
    }

    checkWarning(dbOnly.isEmpty()) {
      "Hibernate missing tables ${dbOnly.map { it.name }}"
    }

    for ((dbTable, hibernateTable) in intersectionPairs) {
      withDeclaration(hibernateTable.snakeCaseName) {
        validateTables(dbTable, hibernateTable)
      }
    }
  }

  private fun validateTables(dbTable: TableDeclaration, hibernateTable: TableDeclaration) {
    val (dbOnly, hibernateOnly, intersectionPairs) = splitChildren(
        dbTable.columns,
        hibernateTable.columns)

    validate(dbTable.snakeCaseName == dbTable.name) {
      "Database table name \"${dbTable.name}\" should be in lower_snake_case"
    }
    validate(dbTable.name == hibernateTable.name) {
      "Database table name \"${dbTable.name}\" should exactly match hibernate \"${hibernateTable.name}\""
    }

    validate(hibernateOnly.isEmpty()) {
      "Database table \"${dbTable.name}\" is missing columns ${hibernateOnly.map { it.name }} found in hibernate \"${hibernateTable.name}\""
    }

    // TODO (maacosta) how strict should we be here? If `DEFAULT NULL` column exists in the Db and hibernate
    //                does not know about it hibernate can still do writes to db. However if we are going to do
    //                lookups on this column it might not work.. do we look at queries?.
    validate(dbOnly.isEmpty()) {
      "Hibernate entity \"${hibernateTable.name}\" is missing columns ${dbOnly.map { it.name }} expected in table \"${dbTable.name}\""
    }

    for ((dbColumn, hibernateColumn) in intersectionPairs) {
      withDeclaration(hibernateColumn.snakeCaseName) {
        validateColumns(dbColumn, hibernateColumn)
      }
    }
  }

  private fun validateColumns(dbColumn: ColumnDeclaration, hibernateColumn: ColumnDeclaration) {
    validate(dbColumn.snakeCaseName == dbColumn.name) {
      "Column ${dbColumn.name} should be in lower_snake_case"
    }
    validate(dbColumn.name == hibernateColumn.name) {
      "Column ${dbColumn.name} should exactly match hibernate ${hibernateColumn.name}"
    }

    // We have that the hibernate column only needs to be null if the database is null.
    // It's okay if hibernate is more strict.
    validate(dbColumn.nullable || !hibernateColumn.nullable) {
      "Column ${dbColumn.name} is NOT NULL in database but ${hibernateColumn.name} is nullable in hibernate"
    }
  }

  private fun <C : Declaration> splitChildren(
    firstSchemaChildren: List<C>,
    secondSchemaChildren: List<C>
  ): Triple<List<C>, List<C>, List<Pair<C, C>>> {

    // Look for duplicate identifiers.
    val duplicateFirstChildren =
        firstSchemaChildren
            .asSequence()
            .groupBy { it.snakeCaseName }
            .filter { it.value.size > 1 }

    validate(duplicateFirstChildren.isEmpty()) {
      val duplicatesList =
          duplicateFirstChildren.map { duplicates -> duplicates.value.map { it.name } }
      "Duplicate identifiers: $duplicatesList"
    }

    val duplicateSecondChildren =
        secondSchemaChildren
            .asSequence()
            .groupBy { it.snakeCaseName }
            .filter { it.value.size > 1 }

    validate(duplicateSecondChildren.isEmpty()) {
      val duplicatesList =
          duplicateSecondChildren.map { duplicates -> duplicates.value.map { it.name } }
      "Duplicate identifiers: $duplicatesList"
    }

    // Continue to compare those that are unique.
    val uniqueFirstChildren = firstSchemaChildren - duplicateFirstChildren.flatMap { it.value }
    val uniqueSecondChildren = secondSchemaChildren - duplicateSecondChildren.flatMap { it.value }

    // Find all children missing in the secondSchema.
    val uniqueSecondNames = uniqueSecondChildren.map { it.snakeCaseName }
    val (firstIntersection, firstOnly) =
        uniqueFirstChildren.partition {
          it.snakeCaseName in uniqueSecondNames
        }

    // Find all children missing in the firstSchema.
    val uniqueFirstNames = uniqueFirstChildren.map { it.snakeCaseName }
    val (secondIntersection, secondOnly) =
        uniqueSecondChildren.partition { it.snakeCaseName in uniqueFirstNames }

    // Sort the common children and pair them off in this order
    val intersectionPairs = firstIntersection.sortedBy { it.snakeCaseName }
        .zip(secondIntersection.sortedBy { it.snakeCaseName })

    return Triple(firstOnly, secondOnly, intersectionPairs)
  }

  /**
   * When expression is false should create an error using lambda().
   * lambda should return and error message corresponding to the case when expression is false.
   */
  private fun validate(expression: Boolean, lambda: () -> String) {
    if (!expression) {
      messages += Message(pathBreadcrumbs.toList(), lambda(), true)
    }
  }

  private fun checkWarning(expression: Boolean, lambda: () -> String) {
    if (!expression) {
      messages += Message(pathBreadcrumbs.toList(), lambda(), false)
    }
  }

  private fun withDeclaration(name: String, lambda: () -> Unit) {
    pathBreadcrumbs.add(name)
    try {
      lambda()
    } finally {
      pathBreadcrumbs.dropLast(1)
    }
  }

  /**
   * Adds a message that may be useful for the developer to debug. Should not register an error.
   */
  private fun info(message: String) {
    messages += Message(pathBreadcrumbs.toList(), message, false)
  }

  private fun <R> SessionFactory.doWork(lambda: Connection.() -> R): R {
    openSession().use { session ->
      return session.doReturningWork { connection ->
        connection.lambda()
      }
    }
  }
}

/**
 * An SQL database, table, or column for the purposes of static analysis.
 */
internal abstract class Declaration {
  abstract val name: String
  val snakeCaseName: String by lazy {
    name.toSnakeCase()
  }
}

internal data class DatabaseDeclaration(
  override val name: String,
  val tables: List<TableDeclaration>
) : Declaration()

internal data class TableDeclaration(
  override val name: String,
  val columns: List<ColumnDeclaration>
) : Declaration()

internal data class ColumnDeclaration(
  override val name: String,
  val nullable: Boolean
) : Declaration()

internal data class Message(
  val path: List<String>,
  val text: String,
  val error: Boolean = true
)

/**
 * Returns a snake case version of the current string. "MarioAcosta" returns "mario_acosta", and
 * "Coca-Cola" returns "coca_cola".
 */
internal fun String.toSnakeCase(): String =
    if (contains(Regex("([_\\-])"))) {
      replace("-", "_").toLowerCase()
    } else {
      // Lets guess the identifier is in CamelCase.
      CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, this)
    }
