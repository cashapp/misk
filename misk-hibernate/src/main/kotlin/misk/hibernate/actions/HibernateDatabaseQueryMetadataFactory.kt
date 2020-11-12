package misk.hibernate.actions

import misk.hibernate.Constraint
import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Order
import misk.hibernate.Query
import misk.hibernate.Select
import misk.web.RequestTypes.Companion.maybeCreatePrimitiveField
import misk.web.metadata.DatabaseQueryMetadata
import misk.web.metadata.Field
import javax.inject.Singleton
import javax.persistence.Table
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation

@Singleton
class HibernateDatabaseQueryMetadataFactory {
  fun <T : DbEntity<T>> fromQuery(
    dbEntity: KClass<T>,
    queryClass: KClass<out Query<T>>
  ): DatabaseQueryMetadata {
    val table = dbEntity.findAnnotation<Table>()!!

    val constraintsResult = mutableMapOf<String, Field?>()
    val ordersResult = mutableMapOf<String, Field>()
    val selectsResult = mutableMapOf<String, Field>()

    for (function in queryClass.criteriaFunctions()) {
      val constraint = function.findAnnotation<Constraint>()
      val select = function.findAnnotation<Select>()
      val order = function.findAnnotation<Order>()

      when {
        constraint != null -> constraintsResult += constraintPair(function, constraint)
        select != null -> selectsResult += selectPair(function, select)
        order != null -> ordersResult += orderPair(function, order)
      }
    }
    return DatabaseQueryMetadata(
        accessAnnotation = null,
        table = table.name,
        entityClass = dbEntity,
        queryClass = queryClass,
        constraints = constraintsResult,
        orders = ordersResult,
        selects = selectsResult
    )
  }

  private fun constraintPair(function: KFunction<*>, constraint: Constraint): Pair<String, Field?> {
    // Size - 1 since `this` is the first parameter.
    check(function.parameters.size - 1 <= 1)
    val onlyParameter =
        function.parameters.firstOrNull { it.name != null } ?: return function.name to null

    val fieldName = onlyParameter.name!!
    val field = createField(onlyParameter.type, fieldName, false)
    return function.name to field
  }

  private fun createField(
    fieldType: KType,
    fieldName: String,
    repeated: Boolean
  ): Field? {
    val fieldClass = fieldType.classifier as KClass<*>
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
      else -> Field(fieldName, fieldClass.qualifiedName!!, repeated)
    }
  }

  private fun orderPair(function: KFunction<*>, order: Order): Pair<String, Field> {
    TODO("Not yet implemented")
  }

  private fun selectPair(function: KFunction<*>, select: Select): Pair<String, Field> {
    TODO("Not yet implemented")
  }

  private fun <T : DbEntity<T>> KClass<out Query<T>>.criteriaFunctions(): MutableList<KFunction<*>> {
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

}