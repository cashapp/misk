package misk.hibernate

/**
 * MiskSchema provides the structure that we can represent a database and the ORM can be represented
 * for ease of comparison. A MiskSchemaParent is a MiskSchema that contains a single list of other
 * MiskSchemas; for example, a table has a list of columns.
 *
 * The normalizedName field attempts to convert the name to lower_snake_case and is used when the
 * MiskSchema is validated.
 */
abstract class MiskSchema {
  abstract val name: String
  val normalizedName: String by lazy {
    SchemaValidation.normalize(name)
  }
}

abstract class MiskSchemaParent<T : MiskSchema> : MiskSchema() {
  abstract val children: List<T>
}

data class MiskDatabase(
  override val name: String,
  override val children: List<MiskTable>
) : MiskSchemaParent<MiskTable>() {
  val tables: List<MiskTable> get() = children
}

data class MiskTable(
  override val name: String,
  override val children: List<MiskColumn>
) : MiskSchemaParent<MiskColumn>() {
  val columns: List<MiskColumn> get() = children
}

data class MiskColumn(
  override val name: String,
  val nullable: Boolean
) : MiskSchema()
