package misk.web.metadata

/** Metadata front end model for Database Query Misk-Web Tab */
data class DatabaseQueryMetadata(
  val capabilities: Set<String> = setOf(),
  val services: Set<String> = setOf(),
  /** SQL table name */
  val table: String,
  /** DbTable entity class */
  val entityClass: String,
  /** @Constraint functions on Misk Query interface */
  val constraints: Map<String, Field>,
  /** @Order functions on Misk Query interface */
  val orders: Map<String, Field>,
  /** @Select functions on Misk Query interface */
  val selects: Map<String, Field>
)