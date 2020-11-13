package misk.web.metadata

import misk.security.authz.AccessAnnotationEntry
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/** Metadata front end model for Database Query Misk-Web Tab */
data class DatabaseQueryMetadata(
  val queryWebActionPath: String,
  val allowedCapabilities: Set<String> = setOf(),
  val allowedServices: Set<String> = setOf(),
  val accessAnnotation: String?,
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
    queryWebActionPath: String,
    allowedCapabilities: Set<String> = setOf(),
    allowedServices: Set<String> = setOf(),
    accessAnnotation: KClass<out Annotation>? = null,
    table: String,
    entityClass: KClass<*>,
    queryClass: KClass<*>,
    constraints: List<ConstraintMetadata>,
    orders: List<OrderMetadata>,
    selects: List<SelectMetadata>,
    types: Map<String, Type>
  ) : this(
      queryWebActionPath = queryWebActionPath,
      allowedCapabilities = allowedCapabilities,
      allowedServices = allowedServices,
      accessAnnotation = accessAnnotation?.simpleName,
      table = table,
      entityClass = entityClass.simpleName!!, // Assert not null, since this shouldn't be anonymous.
      queryClass = queryClass.simpleName!!, // Assert not null, since this shouldn't be anonymous.
      constraints = constraints,
      orders = orders,
      selects = selects,
      types = types
  )

  data class ConstraintMetadata(
    override val name: String,
    override val parametersTypeName: String,
    val path: String,
    val operator: String
  ) : FunctionMetadata

  data class OrderMetadata(
    override val name: String,
    override val parametersTypeName: String,
    val path: String,
    val ascending: Boolean
  ) : FunctionMetadata

  data class SelectMetadata(
    override val name: String,
    override val parametersTypeName: String,
    val paths: List<String>,
  ) : FunctionMetadata
}
