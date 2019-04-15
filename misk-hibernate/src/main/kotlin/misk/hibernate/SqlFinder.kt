package misk.hibernate

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import io.opentracing.Tracer
import misk.inject.typeLiteral
import org.hibernate.query.NativeQuery
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod

@Singleton
class SqlFinderFactory @Inject constructor(
  @com.google.inject.Inject(optional = true) private var tracer: Tracer? = null
) {
  private val queryMethodHandlersCache = CacheBuilder.newBuilder()
      .build(object : CacheLoader<KClass<*>, Map<Method, SqlQueryMethod>>() {
        override fun load(key: KClass<*>) = SqlFinder.queryMethodHandlers(key)
      })

  fun <T : Any> newFinder(queryClass: KClass<T>): T {
    val queryMethodHandlers = queryMethodHandlersCache[queryClass]
    val classLoader = queryClass.java.classLoader

    @Suppress("UNCHECKED_CAST") // The proxy implements the requested interface.
    return Proxy.newProxyInstance(
        classLoader,
        arrayOf<Class<*>>(queryClass.java),
        SqlFinder(queryMethodHandlers, tracer)
    ) as T
  }
}

internal class SqlFinder constructor(
  private val queryMethodHandlers: Map<Method, SqlQueryMethod>,
  @com.google.inject.Inject(optional = true) private var tracer: Tracer? = null
) : InvocationHandler {

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
    val handler = queryMethodHandlers[method]

    return when {
      handler != null -> {
        val result = handler.invoke(this, args ?: arrayOf())
        if (result == this) proxy else result
      }

      // Pass calls upwards.
      method.declaringClass == Any::class.java -> {
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

  companion object {
    val namedParamRegex = Regex(":[\\w_]")
    val limitOffsetRegex = Regex("\\S(limit|offset)\\S")

    internal fun queryMethodHandlers(
      queryClass: KClass<*>
    ): Map<Method, SqlQueryMethod> {
      val errors = mutableListOf<String>()
      val result = mutableMapOf<Method, SqlQueryMethod>()

      for (function in queryClass.declaredMemberFunctions) {
        val sql = function.findAnnotation<Sql>()
        if (sql == null) {
          errors.add("${function.name}() must be annotated @${Sql::class.simpleName}")
          continue
        }

        createSqlQuery(errors, function, result, sql)
      }

      // TODO(dhanji): Crawl supertypes?

      require(errors.isEmpty()) {
        "Query class ${queryClass.qualifiedName} has problems:" +
            "\n  ${errors.joinToString(separator = "\n  ")}"
      }
      return result
    }

    private fun createSqlQuery(
      errors: MutableList<String>,
      function: KFunction<*>,
      result: MutableMap<Method, SqlQueryMethod>,
      sql: Sql
    ) {

      if (sql.query.contains('?')) {
        errors.add("${function.name}() query contains invalid positional parameters ('?')," +
            " use :named_parameters instead")
      }

      if (limitOffsetRegex.containsMatchIn(sql.query)) {
        errors.add("${function.name}() query contains invalid limit or offset keyword")
      }

      val namedParameters = namedParamRegex.findAll(sql.query)
          .map { param -> param.value }
          .toList()

      // Validate that parameters match.
      function.parameters.forEach { param ->
        if (!namedParameters.contains(param.name)) {
          errors.add("${function.name}() arguments missing named parameter: ${param.name}")
        }
      }

      val returnType = function.returnType.typeLiteral()
      if (returnType != QueryPlan::class) {
        errors.add("${function.name}() return type must be a QueryPlan<T>")
      }

      val javaMethod = function.javaMethod ?: throw UnsupportedOperationException()
      result[javaMethod] = SqlQueryMethod(sql, namedParameters)
    }
  }
}

internal class SqlQueryMethod(
  val sql: Sql,
  val namedParameters: List<String>
) : QueryPlan<Any> {

  var limit = 1000
  var offset = 0
  lateinit var args: Array<out Any>

  @Suppress("UNUSED_PARAMETER") fun invoke(sqlFinder: SqlFinder, args: Array<out Any>): Any? {
    this.args = args
    return this
  }

  override fun limit(max: Int): QueryPlan<Any> {
    this.limit = max
    return this
  }

  override fun offset(start: Int): QueryPlan<Any> {
    this.offset = start
    return this
  }

  override fun list(session: Session): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return prepare(session).list() as List<Any>
  }

  override fun unique(session: Session): Any? {
    return prepare(session).uniqueResult()
  }

  private fun prepare(session: Session): NativeQuery<Any> {
    val nativeQuery = session.hibernateSession.createNativeQuery(sql.query)
    nativeQuery.firstResult = offset
    nativeQuery.maxResults = limit

    args.forEachIndexed { index, value ->
      nativeQuery.setParameter(namedParameters[index], value)
    }
    return nativeQuery
  }
}

interface QueryPlan<T> {
  fun limit(max: Int): QueryPlan<T>

  fun offset(start: Int): QueryPlan<T>

  fun list(session: Session): List<T>

  fun unique(session: Session): T?
}
