package misk.hibernate.actions

import misk.hibernate.Constraint
import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Projection
import misk.hibernate.Property
import misk.hibernate.Query
import misk.hibernate.Select
import misk.hibernate.Session
import misk.hibernate.actions.HibernateDatabaseQueryDynamicAction.Companion.HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH
import misk.hibernate.actions.HibernateDatabaseQueryStaticAction.Companion.HIBERNATE_QUERY_STATIC_WEBACTION_PATH
import misk.inject.typeLiteral
import misk.security.authz.AccessAnnotationEntry
import misk.web.MiskWebFormBuilder.Companion.createEnumField
import misk.web.MiskWebFormBuilder.Companion.createSyntheticEnumField
import misk.web.MiskWebFormBuilder.Companion.maybeCreatePrimitiveField
import misk.web.MiskWebFormBuilder.Field
import misk.web.MiskWebFormBuilder.Type
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess
import javax.inject.Inject
import javax.inject.Singleton
import javax.persistence.Table
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

@Singleton
internal class HibernateDatabaseQueryMetadataFactory @Inject constructor(
  private val accessAnnotationEntries: List<AccessAnnotationEntry>
) {
  fun <T : DbEntity<T>> fromQuery(
    dbEntityClass: KClass<T>,
    queryClass: KClass<out Query<T>>? = null,
    accessAnnotationClass: KClass<out Annotation> = NoAdminDashboardDatabaseAccess::class,
  ): DatabaseQueryMetadata {
    val table = dbEntityClass.findAnnotation<Table>()!!

    val accessAnnotationEntry =
      accessAnnotationEntries.find { it.annotation == accessAnnotationClass }
    val allowedCapabilities = accessAnnotationEntry?.capabilities?.toSet() ?: setOf()
    val allowedServices = accessAnnotationEntry?.services?.toSet() ?: setOf()

    val constraintsResult = mutableListOf<Pair<Type, DatabaseQueryMetadata.ConstraintMetadata>>()
    val ordersResult = mutableListOf<Pair<Type, DatabaseQueryMetadata.OrderMetadata>>()
    val selectsResult = mutableListOf<Pair<Type, DatabaseQueryMetadata.SelectMetadata>>()

    val queryTypesDynamicToInclude = if (queryClass != null) {
      addStaticQueryMethodMetadata(queryClass, constraintsResult, ordersResult, selectsResult)
      getQueryConfigType() + listOf()
    } else {
      makeDynamicQueryTypes(dbEntityClass)
    }

    val queryTypes = queryTypesDynamicToInclude +
      constraintsResult.map { it.second.parametersTypeName to it.first } +
      ordersResult.map { it.second.parametersTypeName to it.first } +
      selectsResult.map { it.second.parametersTypeName to it.first }

    val isDynamic = queryClass == null &&
      constraintsResult.isEmpty() &&
      ordersResult.isEmpty() &&
      selectsResult.isEmpty()
    val queryType = if (isDynamic) {
      makeDynamicQueryTypes(dbEntityClass).toMap()[QUERY_TYPE]!!
    } else {
      Type(
        fields = queryTypes.map { (name, _) ->
          Field(name = name, repeated = false, type = name)
        }
      )
    }

    val queryWebActionPath = if (isDynamic) {
      HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH
    } else {
      HIBERNATE_QUERY_STATIC_WEBACTION_PATH
    }
    return DatabaseQueryMetadata(
      queryWebActionPath = queryWebActionPath,
      allowedCapabilities = allowedCapabilities,
      allowedServices = allowedServices,
      accessAnnotation = accessAnnotationClass,
      table = table.name,
      entityClass = dbEntityClass,
      entitySchema = getDbEntitySchema(dbEntityClass),
      queryClass = queryClass,
      constraints = constraintsResult.map { it.second },
      orders = ordersResult.map { it.second },
      selects = selectsResult.map { it.second },
      types = queryTypes.toMap() + mapOf("queryType" to queryType)
    )
  }

  private fun <T : DbEntity<T>> addStaticQueryMethodMetadata(
    queryClass: KClass<out Query<T>>?,
    constraintsResult: MutableList<Pair<Type, DatabaseQueryMetadata.ConstraintMetadata>>,
    ordersResult: MutableList<Pair<Type, DatabaseQueryMetadata.OrderMetadata>>,
    selectsResult: MutableList<Pair<Type, DatabaseQueryMetadata.SelectMetadata>>
  ) {
    if (queryClass != null) {
      for (function in queryClass.criteriaFunctions()) {
        val constraint = function.findAnnotation<Constraint>()
        val order = function.findAnnotation<Order>()
        val select = function.findAnnotation<Select>()

        when {
          constraint != null -> constraintsResult += constraintMetadata(
            queryClass, function,
            constraint
          )
          order != null -> ordersResult += orderMetadata(queryClass, function, order)
          select != null -> selectsResult += selectMetadata(
            queryClass, function, select,
            function.returnType
          )
        }
      }
    }
  }

  private fun constraintMetadata(
    queryClass: KClass<out Query<*>>,
    function: KFunction<*>,
    constraint: Constraint
  ): Pair<Type, DatabaseQueryMetadata.ConstraintMetadata> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter = function.parameters.firstOrNull { it.name != null }

    val field = onlyParameter?.name?.let { name ->
      createField(onlyParameter.type, name, onlyParameter.isVararg)
    } ?: Field(name = "Add Constraint", type = Boolean::class.simpleName!!, repeated = false)
    return Type(
      fields = listOf(field)
    ) to DatabaseQueryMetadata.ConstraintMetadata(
      name = function.name,
      parametersTypeName = "Constraint/${queryClass.simpleName}/${function.name}",
      path = constraint.path,
      operator = constraint.operator.name
    )
  }

  private fun orderMetadata(
    queryClass: KClass<out Query<*>>,
    function: KFunction<*>,
    order: Order
  ): Pair<Type, DatabaseQueryMetadata.OrderMetadata> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter = function.parameters.firstOrNull { it.name != null }

    val field = onlyParameter?.name?.let { name ->
      createField(onlyParameter.type, name, onlyParameter.isVararg)
    } ?: Field(
      name = "Add Order (path=${order.path}, asc=${order.asc})",
      type = Boolean::class.simpleName!!, repeated = false
    )
    return Type(
      fields = listOf(field)
    ) to DatabaseQueryMetadata.OrderMetadata(
      name = function.name,
      parametersTypeName = "Order/${queryClass.simpleName}/${function.name}",
      path = order.path,
      ascending = order.asc
    )
  }

  private fun selectMetadata(
    queryClass: KClass<out Query<*>>,
    function: KFunction<*>,
    select: Select,
    selectTargetReturnType: KType
  ): Pair<Type, DatabaseQueryMetadata.SelectMetadata> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter = function.parameters.firstOrNull { it.name != null }

    val paths = if (select.path == "") {
      val maybeProjectionClass =
        (selectTargetReturnType.arguments.firstOrNull()?.type?.classifier as KClass<*>?)
          ?.supertypes?.firstOrNull { it.classifier as KClass<*> == Projection::class }
      if (maybeProjectionClass != null) {
        // TODO (adrw) this projection class path parsing isn't working
        val paths = (maybeProjectionClass.classifier as KClass<*>).members.map { member ->
          member.annotations.map {
            if (it.annotationClass == Property::class) {
              (it as Property).path
            } else {
              null
            }
          }.fold(listOf<String>()) { acc, path -> path?.let { acc + it } ?: acc }
        }.reduce { acc, paths -> acc + paths }
        paths
      } else {
        listOf()
      }
    } else {
      listOf(select.path)
    }

    val field = onlyParameter?.name?.let { name ->
      if (onlyParameter.type.classifier as KClass<*> == Session::class) {
        Field(
          name = "Add Select (paths=$paths)", type = Boolean::class.simpleName!!,
          repeated = false
        )
      } else {
        createField(onlyParameter.type, name, onlyParameter.isVararg)
      }
    }

    return Type(
      fields = field?.let { listOf(field) } ?: listOf()
    ) to DatabaseQueryMetadata.SelectMetadata(
      name = function.name,
      parametersTypeName = "Select/${queryClass.simpleName}/${function.name}",
      paths = paths
    )
  }

  private fun createField(
    fieldType: KType,
    fieldName: String,
    repeated: Boolean
  ): Field? {
    val fieldClass = fieldType.typeLiteral().rawType
    val maybeCreatePrimitiveField = maybeCreatePrimitiveField(fieldClass, fieldName, repeated)
    return when {
      maybeCreatePrimitiveField != null -> maybeCreatePrimitiveField
      fieldClass == Id::class -> Field(fieldName, Long::class.simpleName!!, repeated = repeated)
      fieldClass == Collection::class -> {
        require(!repeated) { "Unexpected query with nested lists for criteria" }
        val fieldClassParameters = fieldType.arguments
        require(fieldClassParameters.size == 1) {
          "Encountered Wire-generated List without 1 type parameter: $fieldType"
        }
        val listType = fieldClassParameters[0].type!!
        createField(listType, fieldName, true)
      }
      else -> Field(fieldName, fieldClass.canonicalName, repeated)
    }
  }

  private fun <T : DbEntity<T>> KClass<out Query<T>>.criteriaFunctions():
    MutableList<KFunction<*>> {
      val allMethods = mutableListOf<KFunction<*>>()
      for (function in declaredMemberFunctions) {
        allMethods += function
      }
      for (supertype in allSupertypes) {
        if (supertype.classifier == Query::class || supertype.classifier == Any::class) continue
        val classifier = supertype.classifier as? KClass<*> ?: continue
        for (function in classifier.declaredMemberFunctions) {
          allMethods += function
        }
      }
      return allMethods
    }

  private fun getDbEntitySchema(dbEntityClass: KClass<out DbEntity<*>>): Map<String, KType> =
    dbEntityClass.memberProperties.map { memberProperty ->
      memberProperty.name to memberProperty.returnType
    }.toMap()

  companion object {
    data class DynamicQueryConstraint(
      val path: String?,
      val operator: Operator?,
      val value: String?
    )

    data class DynamicQueryOrder(
      val path: String?,
      val ascending: Boolean? = false
    )

    data class DynamicQuerySelect(
      val paths: List<String>? = listOf()
    )

    data class QueryConfig(
      val maxRows: Int?
    )

    data class DynamicQuery(
      val queryConfig: QueryConfig? = null,
      val constraints: List<DynamicQueryConstraint>? = null,
      val orders: List<DynamicQueryOrder>? = null,
      val select: DynamicQuerySelect? = null,
    )

    const val QUERY_CONFIG_TYPE_NAME = "Config/Query"
    private const val DYNAMIC_CONSTRAINT_TYPE_NAME = "Constraint/Dynamic"
    private const val DYNAMIC_ORDER_TYPE_NAME = "Order/Dynamic"
    private const val DYNAMIC_SELECT_TYPE_NAME = "Select/Dynamic"
    private const val QUERY_TYPE = "queryType"
    private const val QUERY_CONFIG_KEY = "queryConfig"
    private const val DYNAMIC_CONSTRAINTS_KEY = "constraints"
    private const val DYNAMIC_ORDERS_KEY = "orders"
    private const val DYNAMIC_SELECT_KEY = "select"

    private fun getQueryConfigType() = listOf(
      QUERY_CONFIG_TYPE_NAME to Type(
        fields = listOf(
          Field(name = "maxRows", repeated = false, type = "Int"),
        )
      )
    )

    fun makeDynamicQueryTypes(dbEntityClass: KClass<out DbEntity<*>>): List<Pair<String, Type>> =
      getQueryConfigType() + listOf(
        DYNAMIC_CONSTRAINT_TYPE_NAME to Type(
          fields = listOf(
            createSyntheticEnumField(
              fieldClassName = "${dbEntityClass.simpleName!!}Paths",
              fieldName = "path",
              enumValues = dbEntityClass.memberProperties.map { it.name },
              repeated = false
            ),
            createEnumField(Operator::class.java, "operator", false),
            Field(name = "value", repeated = false, type = "String"),
          )
        ),
        DYNAMIC_ORDER_TYPE_NAME to Type(
          fields = listOf(
            createSyntheticEnumField(
              fieldClassName = "${dbEntityClass.simpleName!!}Paths",
              fieldName = "path",
              enumValues = dbEntityClass.memberProperties.map { it.name },
              repeated = false
            ),
            Field(name = "ascending", repeated = false, type = "Boolean"),
          )
        ),
        DYNAMIC_SELECT_TYPE_NAME to Type(
          fields = listOf(
            createSyntheticEnumField(
              fieldClassName = "${dbEntityClass.simpleName!!}Paths",
              fieldName = "paths",
              enumValues = dbEntityClass.memberProperties.map { it.name },
              repeated = true
            )
          )
        ),
        QUERY_TYPE to Type(
          fields = listOf(
            Field(
              name = QUERY_CONFIG_KEY,
              repeated = false,
              type = QUERY_CONFIG_TYPE_NAME
            ),
            Field(
              name = DYNAMIC_CONSTRAINTS_KEY, repeated = true,
              type = DYNAMIC_CONSTRAINT_TYPE_NAME
            ),
            Field(name = DYNAMIC_ORDERS_KEY, repeated = true, type = DYNAMIC_ORDER_TYPE_NAME),
            Field(name = DYNAMIC_SELECT_KEY, repeated = false, type = DYNAMIC_SELECT_TYPE_NAME),
          )
        ),
      )
  }
}
