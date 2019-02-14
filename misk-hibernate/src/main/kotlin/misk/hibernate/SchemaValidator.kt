package misk.hibernate

import com.google.common.base.CaseFormat
import misk.jdbc.uniqueString
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Column
import java.sql.Connection
import kotlin.reflect.full.memberProperties

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
    transacter: Transacter,
    hibernateMetadata: Metadata
  ) {
    val hibernateSchema = readDeclarationFromHibernate(hibernateMetadata)

    val allDbTables = LinkedHashSet<TableDeclaration>()

    transacter.shards().forEach { shard ->
      val dbSchema = transacter.transaction(shard) { s ->
        s.hibernateSession.doReturningWork { readDeclarationFromDatabase(it) }
      }

      withDeclaration(dbSchema.name) {
        validateDatabase(dbSchema, hibernateSchema)
      }
      allDbTables.addAll(dbSchema.tables)
    }

    val (dbOnly, hibernateOnly, _) = splitChildren(
        allDbTables.toList(), hibernateSchema.tables)

    validate(hibernateOnly.isEmpty()) {
      "Database missing tables ${hibernateOnly.map { it.name }}"
    }

    checkWarning(dbOnly.isEmpty()) {
      "Hibernate missing tables ${dbOnly.map { it.name }}"
    }

    throwIfErrorsFound()
  }

  private fun throwIfErrorsFound() {
    check(!messages.any { it.error }) {
      reportString()
    }
  }

  private fun readDeclarationFromDatabase(
    connection: Connection
  ): DatabaseDeclaration {
    val ignoreTables = arrayOf("schema_version")
    connection.createStatement().use { tablesStmt ->
      val tablesRs = tablesStmt.executeQuery(
          "SELECT * FROM information_schema.tables WHERE table_schema = database()")

      val schemaTables = mutableListOf<TableDeclaration>()
      var tableSchema : String? = null

      while (tablesRs.next()) {
        val tableName = tablesRs.getString("TABLE_NAME")
        if (tableName in ignoreTables) continue
        tableSchema = tablesRs.getString("TABLE_SCHEMA")
        val columns = connection.prepareStatement(
            "SELECT * FROM information_schema.columns WHERE table_schema = ? AND table_name = ?"
        ).use { columnsStmt ->
          columnsStmt.setString(1, tableSchema)
          columnsStmt.setString(2, tableName)

          val columnResultSet = columnsStmt.executeQuery()
          val columns = mutableListOf<ColumnDeclaration>()

          while (columnResultSet.next()) {
            columns += ColumnDeclaration(
                name = columnResultSet.getString("COLUMN_NAME"),
                nullable = columnResultSet.getString("IS_NULLABLE") == "YES",
                hasDefaultValue = columnResultSet.getString("COLUMN_DEFAULT")?.isNotBlank() ?: false)
          }

          columns
        }
        schemaTables += TableDeclaration(tableName, columns)
      }
      return DatabaseDeclaration(tableSchema ?: "unknown", schemaTables)
    }
  }

  private fun readDeclarationFromHibernate(
    metadata: Metadata
  ): DatabaseDeclaration {
    val hibernateTables = mutableListOf<TableDeclaration>()
    val entityBindingsIt = metadata.getEntityBindings().iterator()
    //val tableIt = metadata.collectTableMappings().iterator()

    while (entityBindingsIt.hasNext()) {
      val entity = entityBindingsIt.next()
      val table = entity.table
      val tableName = table.name
      val kClass = Class.forName(entity.className).kotlin
      /*
       * Since we're working in kotlin land, we need to see if *kotlin* is forbidding us from using
       * null in addition to checking if we've specified nullability in the hibernate annotation.
       */
      val propertiesByNullability = kClass.memberProperties
          .map({ it.name to it.returnType.isMarkedNullable })
          .toMap()

      val columns = mutableListOf<ColumnDeclaration>()
      val identifierProperty = entity.identifierProperty
      val properties = mutableListOf<org.hibernate.mapping.Property>()
      entity.propertyIterator.forEach {
        val property = it as org.hibernate.mapping.Property
        properties.add(property)
      }
      // equivalent to identifierProperty.isComposite()
      val identifierPropertyValue = identifierProperty.value
      if (identifierPropertyValue is org.hibernate.mapping.Component) {
        identifierPropertyValue.propertyIterator.forEach {
          val property = it as org.hibernate.mapping.Property
          // TODO(jayestrella): This is hard coded, since rootId is a property but it isn't
          //                    really a property that has a column. Find a way to disambiguate
          if (property.name != "rootId") {
            properties.add(property)
          }
        }
      } else {
        properties.add(identifierProperty)
      }

      properties.forEach { property ->
        val columnIt = property.columnIterator
        validate(columnIt.hasNext()) {
          "A property should only be mapped to one column. How did this happen?"
        }
        val column = columnIt.next() as Column
        validate(!columnIt.hasNext()) {
          "A property should only be mapped to one column. How did this happen?"
        }
        validate(propertiesByNullability.containsKey(property.name)) {
          "A property should be the field name not the column name. " +
              "How did we find a field name not found by reflection?"
        }
        val nullable = column.isNullable && propertiesByNullability[property.name]!!
        columns += ColumnDeclaration(column.name, nullable,
            column.defaultValue?.isNotBlank() ?: false)
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

  private fun validateDatabase(
    dbSchema: DatabaseDeclaration,
    hibernateSchema: DatabaseDeclaration
  ) {
    val (_, _, intersectionPairs) = splitChildren(
        dbSchema.tables, hibernateSchema.tables)

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

    validate(dbOnly.isEmpty() || dbOnly.all { it.hasDefaultValue || it.nullable }) {
      "Hibernate entity \"${hibernateTable.name}\" is missing columns ${dbOnly.filter { !(it.hasDefaultValue || it.nullable) }.map { it.name }} expected in table \"${dbTable.name}\""
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

    // We have that the hibernate column only needs to be nullable if the database is nullable.
    // It's okay if hibernate is more strict. However, we shouldn't care that much if the column has a default value
    // dbColumn.nullable
    if (!dbColumn.nullable) {
      validate(!hibernateColumn.nullable || dbColumn.hasDefaultValue) {
        "Column ${dbColumn.name} is NOT NULL in database but ${hibernateColumn.name} is nullable in hibernate"
      }
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
      pathBreadcrumbs.removeAt(pathBreadcrumbs.size - 1)
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
  val nullable: Boolean,
  val hasDefaultValue: Boolean
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
