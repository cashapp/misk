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
  /** @Constraint functions on Misk Query interface, maps function simpleName to Type */
  val constraints: List<ConstraintMetadata>,
  /** @Order functions on Misk Query interface, maps function simpleName to Type */
  val orders: List<OrderMetadata>,
  /** @Select functions on Misk Query interface, maps function simpleName to Type */
  val selects: List<SelectMetadata>,
  /** Contains all Types across all queries */
  val types: Map<String, Type>
) {
  constructor(
    allowedCapabilities: Set<String> = setOf(),
    allowedServices: Set<String> = setOf(),
    accessAnnotation: Annotation,
    table: String,
    entityClass: KClass<*>,
    queryClass: KClass<*>,
    constraints: List<ConstraintMetadata>,
    orders: List<OrderMetadata>,
    selects: List<SelectMetadata>,
    types: Map<String, Type>
  ) : this(
      allowedCapabilities = allowedCapabilities,
      allowedServices = allowedServices,
      accessAnnotation = accessAnnotation.toString(),
      table = table,
      entityClass = ClassNameFormatter.format(entityClass::class),
      queryClass = ClassNameFormatter.format(queryClass::class),
      constraints = constraints,
      orders = orders,
      selects = selects,
      types = types
  )

  data class ConstraintMetadata(
    override val name: String,
    override val parametersType: String,
    val path: String,
    val operator: String
  ) : FunctionMetadata

  data class OrderMetadata(
    override val name: String,
    override val parametersType: String,
    val path: String,
    val operator: String
  ) : FunctionMetadata

  data class SelectMetadata(
    override val name: String,
    override val parametersType: String,
    val path: String,
    val operator: String
  ) : FunctionMetadata
}
