package misk.hibernate

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.inject.TypeLiteral
import misk.inject.typeLiteral
import misk.logging.getLogger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import javax.persistence.criteria.Selection
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaMethod

private val logger = getLogger<ReflectionQuery<*>>()

/**
 * Implements the [Query] @[Constraint] methods using a dynamic proxy, and projections using
 * reflection to call the primary constructor.
 */
internal class ReflectionQuery<T : DbEntity<T>>(
  private val queryClass: KClass<*>,
  private val rootEntityType: KClass<T>,
  private val queryMethodHandlers: Map<Method, QueryMethodHandler>,
  private val queryLimitsConfig: QueryLimitsConfig,

  /** True if this query only exists to collect predicates for an OR clause. */
  private val predicatesOnly: Boolean = false
) : Query<T>, InvocationHandler {
  override var maxRows = -1
    set(value) {
      require(value == -1 || (value in 1..queryLimitsConfig.maxMaxRows)) { "out of range: $value" }
      field = value
    }

  override var firstResult = 0
    set(value) {
      require(value >= 0) { "out of range: $value" }
      field = value
    }

  private val constraints = mutableListOf<PredicateFactory>()
  private val orderFactories = mutableListOf<OrderFactory>()

  private val disabledChecks: EnumSet<Check> = EnumSet.noneOf(Check::class.java)

  override fun disableCheck(check: Check) {
    disabledChecks += check
  }

  override fun <Q : Query<*>> clone(): Q {
    val copy = ReflectionQuery(
        this.queryClass,
        this.rootEntityType,
        this.queryMethodHandlers,
        this.queryLimitsConfig
    )
    if (copy.maxRows != -1) {
      copy.maxRows = this.maxRows
    }
    if (copy.firstResult != 0) {
      copy.firstResult = this.firstResult
    }
    copy.constraints.addAll(this.constraints)
    copy.orderFactories.addAll(this.orderFactories)
    copy.disabledChecks.addAll(this.disabledChecks)

    @Suppress("UNCHECKED_CAST")
    return copy.toProxy() as Q
  }

  override fun dynamicAddConstraint(path: String, operator: Operator, value: Any?) {
    val pathList = path.split('.')
    addConstraint(pathList, operator, value)
  }

  @Suppress("UNCHECKED_CAST") // Comparison operands must be comparable!
  fun addConstraint(path: List<String>, operator: Operator, value: Any?) {
    when (operator) {
      Operator.EQ -> {
        addConstraint { root, builder ->
          builder.equal(root.traverse<Any?>(path), value)
        }
      }
      Operator.NE -> {
        addConstraint { root, builder ->
          builder.notEqual(root.traverse<Any?>(path), value)
        }
      }
      Operator.LT -> {
        val arg = value as Comparable<Comparable<*>?>?
        addConstraint { root, builder ->
          builder.lessThan(root.traverse(path), arg)
        }
      }
      Operator.LE -> {
        val arg = value as Comparable<Comparable<*>?>?
        addConstraint { root, builder ->
          builder.lessThanOrEqualTo(root.traverse(path), arg)
        }
      }
      Operator.GE -> {
        addConstraint { root, builder ->
          val arg = value as Comparable<Comparable<*>?>?
          builder.greaterThanOrEqualTo(root.traverse(path), arg)
        }
      }
      Operator.GT -> {
        addConstraint { root, builder ->
          val arg = value as Comparable<Comparable<*>?>?
          builder.greaterThan(root.traverse(path), arg)
        }
      }
      Operator.IN -> {
        val collection = value as Collection<*>
        addConstraint { root, builder ->
          builder.addInClause(root.traverse(path), collection)
        }
      }
      Operator.NOT_IN -> {
        val collection = value as Collection<*>
        addConstraint { root, builder ->
          builder.not(builder.addInClause(root.traverse(path), collection))
        }
      }
      Operator.IS_NOT_NULL -> {
        addConstraint { root, builder ->
          builder.isNotNull(root.traverse<Comparable<Comparable<*>>>(path))
        }
      }
      Operator.IS_NULL -> {
        addConstraint { root, builder ->
          builder.isNull(root.traverse<Comparable<Comparable<*>>>(path))
        }
      }
    }
  }

  override fun dynamicAddOrder(path: String, asc: Boolean) {
    val pathList = path.split('.')
    addOrderBy { root, builder ->
      if (asc) {
        builder.asc(root.traverse<Any?>(pathList))
      } else {
        builder.desc(root.traverse<Any?>(pathList))
      }
    }
  }

  override fun uniqueResult(session: Session): T? {
    val list = select(false, session)
    return list.firstOrNull()
  }

  override fun dynamicUniqueResult(session: Session, projectedPaths: List<String>): List<Any?>? {
    val list = select(false, session, projectedPaths)
    return list.firstOrNull()
  }

  override fun dynamicUniqueResult(
    session: Session,
    selection: (CriteriaBuilder, Root<T>) -> Selection<out Any>
  ): List<Any?>? {
    val list = select(false, session, selection)
    return list.firstOrNull()
  }

  override fun delete(session: Session): Int {
    check(!predicatesOnly) { "cannot delete on this query" }

    check(orderFactories.size == 0) { "orderBy shouldn't be used for a delete" }
    check(firstResult == 0) { "firstResult shouldn't be used for a delete" }

    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val criteria = criteriaBuilder.createCriteriaDelete(rootEntityType.java)
    val queryRoot = criteria.from(rootEntityType.java)
    val predicate = buildWherePredicate(queryRoot, criteriaBuilder)
    criteria.where(predicate)

    val query = session.hibernateSession
        .createQuery(criteria)

    return session.disableChecks(disabledChecks) {
      query.executeUpdate()
    }

  }

  override fun list(session: Session): List<T> {
    return select(true, session)
  }

  override fun dynamicList(session: Session, projectedPaths: List<String>): List<List<Any?>> {
    return select(true, session, projectedPaths)
  }

  override fun dynamicList(
    session: Session,
    selection: (CriteriaBuilder, Root<T>) -> Selection<out Any>
  ): List<List<Any?>> {
    return select(true, session, selection)
  }

  private fun select(
    returnList: Boolean,
    session: Session,
    projectedPaths: List<String>
  ): List<List<Any?>> {
    val splitProjectedPaths = projectedPaths.map { it.split('.') }
    return select(returnList, session) { criteriaBuilder, queryRoot ->
      criteriaBuilder.array(*splitProjectedPaths.map {
        queryRoot.traverse<Any?>(it)
      }.toTypedArray())
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun select(
    returnList: Boolean,
    session: Session,
    selection: (CriteriaBuilder, Root<T>) -> Selection<out Any>
  ): List<List<Any?>> {
    check(!predicatesOnly) { "cannot select on this query" }

    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(Any::class.java)
    val queryRoot = query.from(rootEntityType.java)

    val predicate = buildWherePredicate(queryRoot, criteriaBuilder)
    query.where(predicate)

    query.orderBy(buildOrderBys(queryRoot, criteriaBuilder))

    query.select(selection(criteriaBuilder, queryRoot))

    val typedQuery = session.hibernateSession.createQuery(query)
    typedQuery.firstResult = firstResult
    typedQuery.maxResults = effectiveMaxRows(returnList)
    val rows = session.disableChecks(disabledChecks) {
      typedQuery.list()
    }
    checkRowCount(returnList, rows.size)
    val result = rows.map {
      when (it) { // If there's exactly one cell per row, Hibernate doesn't wrap it in an array
        is Array<*> -> listOf(*it)
        else -> listOf(it)
      }
    }
    return result
  }

  private fun select(returnList: Boolean, session: Session): List<T> {
    check(!predicatesOnly) { "cannot select on this query" }

    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(rootEntityType.java)
    val queryRoot = query.from(rootEntityType.java)

    val predicate = buildWherePredicate(queryRoot, criteriaBuilder)
    query.where(predicate)

    query.orderBy(buildOrderBys(queryRoot, criteriaBuilder))

    val typedQuery = session.hibernateSession.createQuery(query)
    typedQuery.firstResult = firstResult
    typedQuery.maxResults = effectiveMaxRows(returnList)
    val rows = session.disableChecks(disabledChecks) {
      typedQuery.list()
    }
    checkRowCount(returnList, rows.size)
    return rows
  }

  override fun count(session: Session): Long {
    check(!predicatesOnly) { "cannot select on this query" }

    check(orderFactories.size == 0) { "orderBy shouldn't be used for a count" }
    check(firstResult == 0) { "firstResult shouldn't be used for a count" }
    check(maxRows == -1) { "maxRows shouldn't be used for a count" }

    val criteriaBuilder = session.hibernateSession.criteriaBuilder
    val query = criteriaBuilder.createQuery(Long::class.java)
    val queryRoot = query.from(rootEntityType.java)

    val predicate = buildWherePredicate(queryRoot, criteriaBuilder)
    query.where(predicate)

    query.select(criteriaBuilder.count(queryRoot))

    val typedQuery = session.hibernateSession.createQuery(query)
    return session.disableChecks(disabledChecks) {
      typedQuery.singleResult
    }
  }

  private fun effectiveMaxRows(returnList: Boolean): Int {
    return when {
      (maxRows == 1) -> 1 // If specifically setting maxRows to 1 leave it so we can use unique
      !returnList -> 2 // Detect if the result wasn't actually unique.
      (maxRows != -1) -> maxRows
      else -> queryLimitsConfig.maxMaxRows + 1 // Detect if the result was truncated.
    }
  }

  private fun checkRowCount(returnList: Boolean, rowCount: Int) {
    if (!returnList) {
      check(rowCount <= 1) { "query expected a unique result but was $rowCount" }
    } else if (maxRows == -1) {
      when {
        rowCount > queryLimitsConfig.maxMaxRows -> {
          throw IllegalStateException("query truncated at $rowCount rows")
        }
        rowCount > queryLimitsConfig.rowCountErrorLimit -> {
          logger.error("Unbounded query returned $rowCount rows. " +
              "(Specify maxRows to suppress this error)")
        }
        rowCount > queryLimitsConfig.rowCountWarningLimit -> {
          logger.warn("Unbounded query returned $rowCount rows. " +
              "(Specify maxRows to suppress this warning)")
        }
      }
    }
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
    val handler = queryMethodHandlers[method]

    return when {
      handler != null -> {
        val result = handler.invoke(this, args ?: arrayOf())
        if (result == this) proxy else result
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

  private fun toProxy(): Query<T> {
    val classLoader = queryClass.java.classLoader

    @Suppress("UNCHECKED_CAST") // The proxy implements the requested interface.
    return Proxy.newProxyInstance(
        classLoader, arrayOf<Class<*>>(queryClass.java), this) as Query<T>
  }

  @Singleton
  internal class Factory @Inject internal constructor(private var queryLimitsConfig: QueryLimitsConfig) :
      Query.Factory {
    private val queryMethodHandlersCache = CacheBuilder.newBuilder()
        .build(object : CacheLoader<KClass<*>, Map<Method, QueryMethodHandler>>() {
          override fun load(key: KClass<*>) = queryMethodHandlers(key)
        })

    override fun <Q : Query<*>> newQuery(queryClass: KClass<Q>): Q {
      val queryMethodHandlers = queryMethodHandlersCache[queryClass]
      val queryType = queryClass.typeLiteral().getSupertype(Query::class.java)

      @Suppress("UNCHECKED_CAST") // Hack because we don't have a parameter for the runtime type.
      val entityType =
          (queryType.type as ParameterizedType).actualTypeArguments[0] as Class<DbPlaceholder>

      @Suppress("UNCHECKED_CAST")
      val reflectionQuery = ReflectionQuery(
          queryClass as KClass<DbPlaceholder>,
          entityType.kotlin,
          queryMethodHandlers,
          queryLimitsConfig
      )
      @Suppress("UNCHECKED_CAST")
      return reflectionQuery.toProxy() as Q
    }

    override fun <E : DbEntity<E>> dynamicQuery(entityClass: KClass<E>): Query<E> {
      val reflectionQuery = ReflectionQuery(
          Query::class,
          entityClass,
          mapOf(),
          queryLimitsConfig
      )
      return reflectionQuery.toProxy()
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

  class SelectMethodHandler(
    private val returnList: Boolean,
    private val constructor: KFunction<*>?,
    private val properties: List<List<String>>
  ) : QueryMethodHandler {

    override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
      check(!reflectionQuery.predicatesOnly) { "cannot select on this query" }

      val session = args[0] as Session

      val criteriaBuilder = session.hibernateSession.criteriaBuilder
      val query = criteriaBuilder.createQuery(Any::class.java)
      val root: Root<*> = query.from(reflectionQuery.rootEntityType.java)

      query.select(select(criteriaBuilder, root))
      query.where(reflectionQuery.buildWherePredicate(root, criteriaBuilder))
      query.orderBy(reflectionQuery.buildOrderBys(root, criteriaBuilder))
      val typedQuery = session.hibernateSession.createQuery(query)
      typedQuery.firstResult = reflectionQuery.firstResult
      typedQuery.maxResults = reflectionQuery.effectiveMaxRows(returnList)
      val rows = session.disableChecks(reflectionQuery.disabledChecks) {
        typedQuery.list()
      }
      reflectionQuery.checkRowCount(returnList, rows.size)
      val list = rows.map { toValue(it) }
      return if (returnList) list else list.firstOrNull()
    }

    private fun select(
      criteriaBuilder: CriteriaBuilder,
      queryRoot: Root<*>
    ) = criteriaBuilder.array(*properties.map { queryRoot.traverse<Any?>(it) }.toTypedArray())

    private fun toValue(row: Any): Any? {
      return when {
        constructor == null -> row
        properties.size == 1 -> constructor.call(row)
        else -> constructor.call(*(row as Array<*>))
      }
    }
  }

  /** Handles a query method call. Most implementations add constraints to the method. */
  interface QueryMethodHandler {
    fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any?

    companion object {
      fun create(
        errors: MutableList<String>,
        function: KFunction<*>,
        result: MutableMap<Method, QueryMethodHandler>
      ) {
        val constraint = function.findAnnotation<Constraint>()
        val select = function.findAnnotation<Select>()
        val order = function.findAnnotation<Order>()

        if (constraint != null && select != null) {
          errors.add("${function.name}() has too many annotations")
          return
        }

        if (constraint != null) {
          createConstraint(errors, function, result, constraint)
          return
        }

        if (order != null) {
          createOrder(errors, function, result, order)
          return
        }

        if (select != null) {
          createSelect(errors, function, result, select)
          return
        }

        errors.add("${function.name}() must be annotated @Constraint, @Order or @Select")
      }

      private fun createOrder(
        errors: MutableList<String>,
        function: KFunction<*>,
        result: MutableMap<Method, QueryMethodHandler>,
        order: Order
      ) {
        if (!order.path.matches(PATH_PATTERN)) {
          errors.add("${function.name}() path is not valid: '${order.path}'")
          return
        }

        val path = order.path.split('.')

        val javaMethod = function.javaMethod ?: throw UnsupportedOperationException()
        if (javaMethod.returnType != javaMethod.declaringClass) {
          errors.add("${function.name}() returns ${javaMethod.returnType.name} but " +
              "@Order methods must return this (${javaMethod.declaringClass.name})")
          return
        }

        result[javaMethod] = object : QueryMethodHandler {
          override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
            check(!reflectionQuery.predicatesOnly) { "cannot define sort order on this query" }

            return reflectionQuery.addOrderBy { root, builder ->
              if (order.asc) {
                builder.asc(root.traverse<Any?>(path))
              } else {
                builder.desc(root.traverse<Any?>(path))
              }
            }
          }
        }
      }

      private fun createConstraint(
        errors: MutableList<String>,
        function: KFunction<*>,
        result: MutableMap<Method, QueryMethodHandler>,
        constraint: Constraint
      ) {
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

        // Functions accept 'this' as the first parameter.
        val actualParameterCount = function.parameters.size - 1
        val expectedParameterCount = when (constraint.operator) {
          Operator.IS_NOT_NULL -> 0
          Operator.IS_NULL -> 0
          else -> 1
        }
        if (actualParameterCount != expectedParameterCount) {
          errors.add("${function.name}() declares $actualParameterCount " +
              "parameters but must accept $expectedParameterCount parameters")
          return
        }

        val handler = when (constraint.operator) {
          Operator.EQ -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              return reflectionQuery.addConstraint { root, builder ->
                builder.equal(root.traverse<Any?>(path), args[0])
              }
            }
          }
          Operator.NE -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              return reflectionQuery.addConstraint { root, builder ->
                builder.notEqual(root.traverse<Any?>(path), args[0])
              }
            }
          }
          Operator.LT -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              @Suppress("UNCHECKED_CAST") // Comparison operands must be comparable!
              val arg = args[0] as Comparable<Comparable<*>?>?
              return reflectionQuery.addConstraint { root, builder ->
                builder.lessThan(root.traverse(path), arg)
              }
            }
          }
          Operator.LE -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              @Suppress("UNCHECKED_CAST") // Comparison operands must be comparable!
              val arg = args[0] as Comparable<Comparable<*>?>?
              return reflectionQuery.addConstraint { root, builder ->
                builder.lessThanOrEqualTo(root.traverse(path), arg)
              }
            }
          }
          Operator.GE -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              return reflectionQuery.addConstraint { root, builder ->
                @Suppress("UNCHECKED_CAST") // Comparison operands must be comparable!
                val arg = args[0] as Comparable<Comparable<*>?>?
                builder.greaterThanOrEqualTo(root.traverse(path), arg)
              }
            }
          }
          Operator.GT -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              return reflectionQuery.addConstraint { root, builder ->
                @Suppress("UNCHECKED_CAST") // Comparison operands must be comparable!
                val arg = args[0] as Comparable<Comparable<*>?>?
                builder.greaterThan(root.traverse(path), arg)
              }
            }
          }
          Operator.IN -> {
            val parameter = function.parameters[1]

            val argToCollection: (Any) -> Collection<Any?> = when {
              parameter.isVararg -> { arg -> (arg as Array<*>).toList() }
              parameter.isAssignableTo(Collection::class) -> { arg -> (arg as Collection<*>) }
              else -> {
                errors.add("${function.name}() parameter must be a vararg or a collection")
                return
              }
            }

            object : QueryMethodHandler {
              override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
                return reflectionQuery.addConstraint { root, builder ->
                  val collection = argToCollection(args[0])
                  builder.addInClause(root.traverse(path), collection)
                }
              }
            }
          }
          Operator.NOT_IN -> {
            val parameter = function.parameters[1]

            val argToCollection: (Any) -> Collection<Any?> = when {
              parameter.isVararg -> { arg -> (arg as Array<*>).toList() }
              parameter.isAssignableTo(Collection::class) -> { arg -> (arg as Collection<*>) }
              else -> {
                errors.add("${function.name}() parameter must be a vararg or a collection")
                return
              }
            }

            object : QueryMethodHandler {
              override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
                return reflectionQuery.addConstraint { root, builder ->
                  val collection = argToCollection(args[0])
                  builder.not(builder.addInClause(root.traverse(path), collection))
                }
              }
            }
          }
          Operator.IS_NOT_NULL -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              return reflectionQuery.addConstraint { root, builder ->
                builder.isNotNull(root.traverse<Comparable<Comparable<*>>>(path))
              }
            }
          }
          Operator.IS_NULL -> object : QueryMethodHandler {
            override fun invoke(reflectionQuery: ReflectionQuery<*>, args: Array<out Any>): Any? {
              return reflectionQuery.addConstraint { root, builder ->
                builder.isNull(root.traverse<Comparable<Comparable<*>>>(path))
              }
            }
          }
        }

        result[javaMethod] = handler
      }

      private fun createSelect(
        errors: MutableList<String>,
        function: KFunction<*>,
        result: MutableMap<Method, QueryMethodHandler>,
        select: Select
      ) {
        if (function.parameters.size != 2 ||
            function.parameters[1].type.classifier != Session::class) {
          errors.add("${function.name}() must accept a single Session parameter")
          return
        }

        val returnType = function.returnType.typeLiteral()
        val isList = List::class.java.isAssignableFrom(returnType.rawType)
        val elementType: TypeLiteral<*> = if (isList) {
          val listType = returnType.getSupertype(List::class.java)
          (listType.type as ParameterizedType).actualTypeArguments[0].typeLiteral()
        } else {
          returnType
        }

        if (isList == function.returnType.isMarkedNullable) {
          errors.add("${function.name}() return type must be a non-null List or a nullable value")
          return
        }

        val isProjection = Projection::class.java.isAssignableFrom(elementType.rawType)

        if (!select.path.matches(PATH_PATTERN) && (select.path.isNotEmpty() || !isProjection)) {
          errors.add("${function.name}() path is not valid: '${select.path}'")
          return
        }

        val selectMethodHandler: SelectMethodHandler
        if (isProjection) {
          @Suppress("UNCHECKED_CAST") // The line above confirms that this cast is safe.
          val projectionClass = (elementType.rawType as Class<out Projection>).kotlin
          val constructor = projectionClass.primaryConstructor
          val parameters = if (constructor != null) {
            constructor.parameters
          } else {
            errors.add("${projectionClass.java.name} has no primary constructor")
            return
          }

          val pathPrefix = if (select.path.isEmpty()) "" else "${select.path}."
          val properties = mutableListOf<List<String>>()
          for (parameter in parameters) {
            val property = parameter.findAnnotation<Property>()
            if (property == null) {
              errors.add("${projectionClass.java.name} parameter ${parameter.index} " +
                  "is missing a @Property annotation")
              continue
            }

            if (!property.path.matches(PATH_PATTERN)) {
              errors.add("${projectionClass.java.name} parameter ${parameter.index} " +
                  "path is not valid: '${property.path}'")
              continue
            }

            val path = (pathPrefix + property.path).split('.')
            properties.add(path)
          }
          selectMethodHandler = SelectMethodHandler(isList, constructor, properties)
        } else {
          val onlyPath = select.path.split('.')
          selectMethodHandler = SelectMethodHandler(isList, null, listOf(onlyPath))
        }

        val javaMethod = function.javaMethod ?: throw UnsupportedOperationException()
        result[javaMethod] = selectMethodHandler
      }
    }
  }

  override fun addJpaConstraint(block: PredicateFactory) {
    addConstraint(block)
  }

  internal fun addConstraint(block: PredicateFactory): ReflectionQuery<T> {
    constraints.add(block)
    return this
  }

  private fun buildWherePredicate(root: Root<*>, criteriaBuilder: CriteriaBuilder): Predicate? {
    val predicates = constraints.map { it(root, criteriaBuilder) }

    return if (predicates.size == 1) {
      predicates[0]
    } else {
      criteriaBuilder.and(*predicates.toTypedArray())
    }
  }

  internal fun addOrderBy(orderFactory: OrderFactory): ReflectionQuery<T> {
    orderFactories.add(orderFactory)
    return this
  }

  /**
   * Root: the table root, like 'm' in 'SELECT * FROM movies m ORDER BY m.release_date'
   * CriteriaBuilder: factory for asc, desc
   */
  private fun buildOrderBys(
    root: Root<*>,
    criteriaBuilder: CriteriaBuilder
  ): List<javax.persistence.criteria.Order> {
    return orderFactories.map { it(root, criteriaBuilder) }
  }

  override fun <Q : Query<*>> newOrBuilder(): OrBuilder<Q> {
    val orPredicateFactory = OrClausePredicateFactory<Q>()
    constraints += orPredicateFactory
    return orPredicateFactory
  }

  /**
   * Accept options by creating subqueries, and then aggregate them into a Hibernate predicate.
   */
  inner class OrClausePredicateFactory<Q : Query<*>> : PredicateFactory, OrBuilder<Q> {
    /** Model each option as a query. */
    val options = mutableListOf<ReflectionQuery<T>>()

    /** Builds a single option. */
    override fun option(lambda: Q.() -> Unit) {
      val queryForOption = ReflectionQuery(
          queryClass,
          rootEntityType,
          queryMethodHandlers,
          queryLimitsConfig,
          predicatesOnly = true
      )
      @Suppress("UNCHECKED_CAST") // Q is the query type that we're implementing reflectively.
      (queryForOption.toProxy() as Q).lambda()
      options += queryForOption
    }

    /** Return the full set of options. */
    override fun invoke(root: Root<*>, criteriaBuilder: CriteriaBuilder): Predicate {
      check(options.isNotEmpty()) { "or clause with no options" }
      val choices: List<Predicate?> = options.map { option ->
        check(option.constraints.isNotEmpty()) { "or option with no constraints" }
        option.buildWherePredicate(root, criteriaBuilder)
      }
      return criteriaBuilder.or(*choices.toTypedArray())
    }
  }
}

/** Creates a predicate. This allows us to defer attaching the predicate to the session. */
private typealias PredicateFactory = (root: Root<*>, criteriaBuilder: CriteriaBuilder) -> Predicate

private typealias OrderFactory = (root: Root<*>, criteriaBuilder: CriteriaBuilder) -> javax.persistence.criteria.Order

private val PATH_PATTERN = Regex("""\w+(\.\w+)*""")

/** This placeholder exists so we can create an Id<*>() without a type parameter. */
private class DbPlaceholder : DbEntity<DbPlaceholder> {
  override val id: Id<DbPlaceholder> get() = throw IllegalStateException("unreachable")
}

private fun <T> Path<*>.traverse(chain: List<String>): Path<T> {
  var result = this
  for (segment in chain) {
    result = result.get<Any>(segment)
  }
  @Suppress("UNCHECKED_CAST") // We don't have a mechanism to typecheck paths.
  return result as Path<T>
}

private fun KParameter.isAssignableTo(supertype: KClass<*>) = type.isAssignableTo(supertype)

private fun KType.isAssignableTo(supertype: KClass<*>) =
    supertype.java.isAssignableFrom(typeLiteral().rawType)

private fun CriteriaBuilder.addInClause(
  expression: Path<Any>,
  collection: Collection<Any?>
): Predicate {
  return when {
    collection.isEmpty() -> {
      // The JDBC API forbids empty IN clauses so we use `WHERE 1 = 0` instead to
      // achieve the same result.
      equal(literal(1), 0)
    }
    else -> {
      `in`<Any>(expression).apply {
        for (option in collection) {
          value(option)
        }
      }
    }
  }
}

data class QueryLimitsConfig(
    var maxMaxRows: Int,
    var rowCountErrorLimit: Int,
    var rowCountWarningLimit: Int
)
