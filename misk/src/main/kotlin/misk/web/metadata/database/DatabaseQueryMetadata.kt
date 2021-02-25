package misk.web.metadata.database

import misk.inject.typeLiteral
import misk.web.MiskWebFormBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KType

// TODO(adrw) add local date, date picker support to form
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
  /** Describe the DbEntity with types */
  val entitySchema: Map<String, String>,
  /** Query class */
  val queryClass: String,
  /** @Constraint functions on Misk Query interface, maps function simpleName to Type */
  val constraints: List<ConstraintMetadata>,
  /** @Order functions on Misk Query interface, maps function simpleName to Type */
  val orders: List<OrderMetadata>,
  /** @Select functions on Misk Query interface, maps function simpleName to Type */
  val selects: List<SelectMetadata>,
  /** Contains all Types across all queries */
  val types: Map<String, MiskWebFormBuilder.Type>
) {
  constructor(
    queryWebActionPath: String,
    allowedCapabilities: Set<String> = setOf(),
    allowedServices: Set<String> = setOf(),
    accessAnnotation: KClass<out Annotation>? = null,
    table: String,
    entityClass: KClass<*>,
    entitySchema: Map<String, KType>,
    queryClass: KClass<*>?,
    constraints: List<ConstraintMetadata>,
    orders: List<OrderMetadata>,
    selects: List<SelectMetadata>,
    types: Map<String, MiskWebFormBuilder.Type>
  ) : this(
    queryWebActionPath = queryWebActionPath,
    allowedCapabilities = allowedCapabilities,
    allowedServices = allowedServices,
    accessAnnotation = accessAnnotation?.simpleName,
    table = table,
    entityClass = entityClass.simpleName!!, // Assert not null, since this shouldn't be anonymous.
    entitySchema = entitySchema.mapValues { (_, v) -> v.typeLiteral().type.typeName },
    queryClass = queryClass?.simpleName
      ?: "${entityClass.simpleName!!}$DYNAMIC_QUERY_KCLASS_SUFFIX",
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
  ) : DatabaseQueryFunctionMetadata

  data class OrderMetadata(
    override val name: String,
    override val parametersTypeName: String,
    val path: String,
    val ascending: Boolean
  ) : DatabaseQueryFunctionMetadata

  data class SelectMetadata(
    override val name: String,
    override val parametersTypeName: String,
    val paths: List<String>,
  ) : DatabaseQueryFunctionMetadata

  companion object {
    const val DYNAMIC_QUERY_KCLASS_SUFFIX = "DynamicQuery"
  }
}
