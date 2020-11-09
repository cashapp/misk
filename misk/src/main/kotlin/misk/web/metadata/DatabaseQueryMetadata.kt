package misk.web.metadata

import misk.web.formatter.ClassNameFormatter
import kotlin.reflect.KClass

/** Metadata front end model for Database Query Misk-Web Tab */
data class DatabaseQueryMetadata(
  val allowedCapabilities: Set<String> = setOf(),
  val allowedServices: Set<String> = setOf(),
  val accessAnnotation: String,
  /** SQL table name */
  val table: String,
  /** DbTable entity class */
  val entityClass: String,
  /** Query class */
  val queryClass: String,
  /** @Constraint functions on Misk Query interface */
  val constraints: Map<String, Field>,
  /** @Order functions on Misk Query interface */
  val orders: Map<String, Field>,
  /** @Select functions on Misk Query interface */
  val selects: Map<String, Field>
) {
  constructor(
    allowedCapabilities: Set<String> = setOf(),
    allowedServices: Set<String> = setOf(),
    accessAnnotation: Annotation,
    table: String,
    entityClass: KClass<*>,
    queryClass: KClass<*>,
    constraints: Map<String, Field>,
    orders: Map<String, Field>,
    selects: Map<String, Field>
  ) : this(
      allowedCapabilities = allowedCapabilities,
      allowedServices = allowedServices,
      accessAnnotation = accessAnnotation.toString(),
      table = table,
      entityClass = ClassNameFormatter.format(entityClass::class),
      queryClass = ClassNameFormatter.format(queryClass::class),
      constraints = constraints,
      orders = orders,
      selects = selects
  )
}
