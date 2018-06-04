package misk.hibernate

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import misk.inject.typeLiteral
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import javax.inject.Singleton
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaMethod

/**
 * Implements the [Query] @[Constraint] methods using a dynamic proxy, and projections using
 * reflection to call the primary constructor.
 */
internal class ReflectionQuery<T : DbEntity<T>>(
  private val factory: Factory,
  private val rootEntityType: KClass<T>,
  private val queryMethodHandlers: Map<Method, QueryMethodHandler>
) : Query<T>, InvocationHandler {
  private val constraints = mutableListOf<ConstraintSpec>()

  override fun uniqueResult(session: Session): T? {
    // TODO(jwilson): max results = 2
    val list = list(session)
    require(list.size <= 1) { "expected at most 1 result but was $list" }
    return list.firstOrNull()
  }

  override fun <P : Projection> uniqueResultAs(session: Session, projection: KClass<P>): P? {
    // TODO(jwilson): max results = 2
    val list = listAs(session, projection)
    require(list.size <= 1) { "expected at most 1 result but was $list" }
    return list.firstOrNull()
  }

  override fun list(session: Session): List<T> {
    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(rootEntityType.java)
    val queryRoot = query.from(rootEntityType.java)

    val predicate = buildWherePredicate(queryRoot, criteriaBuilder)
    query.where(predicate)

    val typedQuery = session.hibernateSession.createQuery(query)
    return typedQuery.list()
  }

  override fun <P : Projection> listAs(session: Session, projection: KClass<P>): List<P> {
    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(Array<Any>::class.java)
    val root: Root<*> = query.from(rootEntityType.java)

    @Suppress("UNCHECKED_CAST") // The cache always returns matching types.
    val projectionHandler = factory.projectionCache[projection] as ProjectionHandler<P>

    query.select(projectionHandler.select(criteriaBuilder, root))
    query.where(buildWherePredicate(root, criteriaBuilder))
    val rows = session.hibernateSession.createQuery(query).list()
    return rows.map { projectionHandler.toValue(it) }
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
    val constraintHandler = queryMethodHandlers[method]

    return when {
      constraintHandler != null -> {
        constraintHandler.invoke(this, args ?: arrayOf())
        proxy
      }
      method.declaringClass == Query::class.java || method.declaringClass == Any::class.java -> {
        try {
          val result = method.invoke(this, *(args ?: arrayOf()))
          if (result == this) proxy else result
        } catch (e: InvocationTargetException) {
          throw e.cause!!
        }
      }
      else -> throw UnsupportedOperationException("unexpected call to $method")
    }
  }

  @Singleton
  internal class Factory : Query.Factory {
    val queryMethodHandlersCache = CacheBuilder.newBuilder()
        .build(object : CacheLoader<KClass<*>, Map<Method, QueryMethodHandler>>() {
          override fun load(key: KClass<*>) = queryMethodHandlers(key)
        })
    val projectionCache = CacheBuilder.newBuilder()
        .build(object : CacheLoader<KClass<out Projection>, ProjectionHandler<*>>() {
          override fun load(key: KClass<out Projection>) = ProjectionHandler.create(key)
        })

    override fun <T : Query<*>> newQuery(queryClass: KClass<T>): T {
      val queryMethodHandlers = queryMethodHandlersCache[queryClass]
      val queryType = queryClass.typeLiteral().getSupertype(Query::class.java)

      @Suppress("UNCHECKED_CAST") // Hack because we don't have a parameter for the runtime type.
      val entityType =
          (queryType.type as ParameterizedType).actualTypeArguments[0] as Class<DbPlaceholder>
      val classLoader = queryClass.java.classLoader

      @Suppress("UNCHECKED_CAST") // The proxy implements the requested interface.
      return Proxy.newProxyInstance(
          classLoader,
          arrayOf<Class<*>>(queryClass.java),
          ReflectionQuery(this, entityType.kotlin, queryMethodHandlers)
      ) as T
    }

    private fun queryMethodHandlers(
      queryClass: KClass<*>
    ): Map<Method, QueryMethodHandler> {
      val errors = mutableListOf<String>()
      val result = mutableMapOf<Method, QueryMethodHandler>()
      for (function in queryClass.declaredMemberFunctions) {
        QueryMethodHandler.create(errors, function, result)
      }
      for (supertype in queryClass.allSupertypes) {
        if (supertype.classifier == Query::class || supertype.classifier == Any::class) continue
        val classifier = supertype.classifier as? KClass<*> ?: continue
        for (function in classifier.declaredMemberFunctions) {
          QueryMethodHandler.create(errors, function, result)
        }
      }
      require(errors.isEmpty()) {
        "Query class ${queryClass.java.name} has problems:" +
            "\n  ${errors.joinToString(separator = "\n  ")}"
      }
      return result
    }
  }

  /** Queries for a data class. */
  class ProjectionHandler<P : Projection>(
    val constructor: KFunction<P>,
    val properties: List<List<String>>
  ) {
    fun select(
      criteriaBuilder: CriteriaBuilder,
      queryRoot: Root<*>
    ) = criteriaBuilder.array(*properties.map { queryRoot.traverse(it) }.toTypedArray())

    fun toValue(row: Array<Any>): P = constructor.call(*row)

    companion object {
      fun <P : Projection> create(projectionClass: KClass<P>): ProjectionHandler<P> {
        val errors = mutableListOf<String>()
        val constructor = projectionClass.primaryConstructor

        val parameters = if (constructor != null) {
          constructor.parameters
        } else {
          errors.add("this type has no primary constructor")
          listOf()
        }

        val properties = mutableListOf<List<String>>()
        for (parameter in parameters) {
          val property = parameter.findAnnotation<Property>()
          if (property == null) {
            errors.add("parameter ${parameter.index} is missing a @Property annotation")
            continue
          }

          if (!property.value.matches(PATH_PATTERN)) {
            errors.add("parameter ${parameter.index} path is not valid: '${property.value}'")
            continue
          }
          val path = property.value.split('.')

          properties.add(path)
        }

        require(errors.isEmpty()) {
          "Projection class ${projectionClass.java.name} has problems:" +
              "\n  ${errors.joinToString(separator = "\n  ")}"
        }

        return ProjectionHandler(constructor!!, properties)
      }
    }
  }

  /** Handles a query method call. Most implementations add constraints to the method. */
  interface QueryMethodHandler {
    fun invoke(query: ReflectionQuery<*>, args: Array<out Any>)

    companion object {
      fun create(
        errors: MutableList<String>,
        function: KFunction<*>,
        result: MutableMap<Method, QueryMethodHandler>
      ) {
        val constraint = function.findAnnotation<Constraint>()
        if (constraint == null) {
          errors.add("${function.name}() is missing a @Constraint annotation")
          return
        }

        if (!constraint.path.matches(PATH_PATTERN)) {
          errors.add("${function.name}() path is not valid: '${constraint.path}'")
          return
        }
        val path = constraint.path.split('.')

        val javaMethod = function.javaMethod ?: throw UnsupportedOperationException()
        if (javaMethod.returnType != javaMethod.declaringClass) {
          errors.add("${function.name}() returns ${javaMethod.returnType.name} but " +
              "@Constraint methods must return this (${javaMethod.declaringClass.name})")
          return
        }

        // 2 because functions accept 'this' plus the actual parameters.
        if (function.parameters.size != 2) {
          errors.add("${function.name}() declares ${function.parameters.size - 1} " +
              "parameters but must accept 1 parameter")
          return
        }

        val handler = when (constraint.operator) {
          "=" -> object : QueryMethodHandler {
            override fun invoke(query: ReflectionQuery<*>, args: Array<out Any>) {
              query.addConstraint(path, PredicateSpec.Eq(args[0]))
            }
          }
          "<" -> object : QueryMethodHandler {
            override fun invoke(query: ReflectionQuery<*>, args: Array<out Any>) {
              @Suppress("UNCHECKED_CAST") // The caller must provide Comparable types.
              query.addConstraint(path, PredicateSpec.Lt(args[0] as Comparable<Comparable<*>>))
            }
          }
          else -> {
            errors.add("${function.name}() has an unknown operator: '${constraint.operator}'")
            return
          }
        }

        result[javaMethod] = handler
      }
    }
  }

  /** A constraint that isn't attached to a session. */
  data class ConstraintSpec(
    val path: List<String>,
    val predicate: PredicateSpec
  )

  /** A predicate that isn't attached to a session. */
  sealed class PredicateSpec {
    data class Eq<T>(var value: T) : PredicateSpec() {
      override fun toHibernate(
        criteriaBuilder: CriteriaBuilder,
        path: Path<*>
      ) = criteriaBuilder.equal(path, value)!!
    }

    data class Lt<T : Comparable<T>>(var value: T) : PredicateSpec() {
      @Suppress("UNCHECKED_CAST") // Callers must pass paths whose types match.
      override fun toHibernate(
        criteriaBuilder: CriteriaBuilder,
        path: Path<*>
      ) = criteriaBuilder.lessThan(path as Path<T>, value)!!
    }

    abstract fun toHibernate(criteriaBuilder: CriteriaBuilder, path: Path<*>): Predicate
  }

  internal fun addConstraint(path: List<String>, predicate: PredicateSpec) {
    constraints.add(ConstraintSpec(path, predicate))
  }

  private fun buildWherePredicate(root: Root<*>, criteriaBuilder: CriteriaBuilder): Predicate? {
    val predicates = constraints.map {
      val path = root.traverse(it.path)
      it.predicate.toHibernate(criteriaBuilder, path)
    }

    return if (predicates.size == 1) {
      predicates[0]
    } else {
      criteriaBuilder.and(*predicates.toTypedArray())
    }
  }
}

private val PATH_PATTERN = Regex("""\w+(\.\w+)*""")

/** This placeholder exists so we can create an Id<*>() without a type parameter. */
private class DbPlaceholder : DbEntity<DbPlaceholder> {
  override val id: Id<DbPlaceholder> get() = throw IllegalStateException("unreachable")
}

private fun Path<*>.traverse(chain: List<String>): Path<*> {
  var result = this
  for (segment in chain) {
    result = result.get<Any>(segment)
  }
  return result
}
