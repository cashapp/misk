package misk.hibernate.actions

import com.google.inject.Injector
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.hibernate.Query
import misk.hibernate.ReflectionQuery
import misk.hibernate.Transacter
import misk.inject.typeLiteral
import misk.scope.ActionScoped
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.DatabaseQueryMetadata
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions

/** Runs query against DB and returns results */
@Singleton
class DatabaseQueryAction @Inject constructor(
  @JvmSuppressWildcards private val callerProvider: ActionScoped<MiskCaller?>,
  val databaseQueryMetadata: List<DatabaseQueryMetadata>,
  val queries: List<KClass<out Query<*>>>,
  val injector: Injector,
) : WebAction {

  val maxMaxRows = 40
  val rowCountErrorLimit = 30
  val rowCountWarningLimit = 20
  private val queryFactory = ReflectionQuery.Factory(ReflectionQuery.QueryLimitsConfig(
      maxMaxRows, rowCountErrorLimit, rowCountWarningLimit))

  @Post("/api/database/query/hibernate")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(request: Request): Response {
    val caller = callerProvider.get()!!
    val queryClass = request.queryClass
    val metadata =
        databaseQueryMetadata.find { it.queryClass == queryClass } ?: throw BadRequestException(
            "Invalid Query Class")

    // Find the transacter for the query class
    val query =
        queries.find { it::class.simpleName!! == metadata.queryClass } ?: throw BadRequestException(
            "[query=${metadata.queryClass}] does not exist")
    val transacterBindings = injector.findBindingsByType(Transacter::class.typeLiteral())
    val transacter = transacterBindings.find { transacterBinding ->
      transacterBinding.provider.get().hibernateEntityTypes().map { it.simpleName!! }
          .contains(metadata.entityClass)
    }?.provider?.get() ?: throw BadRequestException(
        "[dbEntity=${metadata.entityClass}] has no associated Transacter")

    val typedQuery = ((query.typeLiteral().getSupertype(
        Query::class.java).type as ParameterizedType).actualTypeArguments.first() as Class<Query<*>>).kotlin

    val results = if (caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
      transacter.transaction { session ->
        queryFactory.newQuery(typedQuery).apply {
          var selectFunction: KFunction<*>? = null

          request.query.forEach { (key, value) ->
            val queryMethodType = key.split("/").first()
            val queryMethodFunctionName = key.split("/").last()
            val function = this::class.functions.find { it.name == queryMethodFunctionName }
                ?: throw BadRequestException(
                    "No function on [query=${metadata.queryClass}] with [name=${queryMethodFunctionName}]")

            when {
              queryMethodType != "Select" -> {
                selectFunction = function
              }
              function.parameters.isEmpty() -> {
                function.call()
              }
              else -> {
                function.call(value)
              }
            }
          }

          if (selectFunction == null) {
            throw BadRequestException(
                "No select function on [query=${metadata.queryClass}], add a select function")
          }

          selectFunction!!.call(session)
        }
      } as Any
    } else {
      throw UnauthorizedException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
    }

    return Response(results = results.toString())
  }

  data class Request(
    val queryClass: String,
    /** Query request takes form of query field name (ie. MovieQuery.releaseDateAsc) to value (true) */
    val query: Map<String, Any>
  )

  data class Response(
    /** In testing, just return string instead of nicely formatted table */
    val results: String
//    val headers: List<String>,
//    val rows: List<List<String>>
  )
}
