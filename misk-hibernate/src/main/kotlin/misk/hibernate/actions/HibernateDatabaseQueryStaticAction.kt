package misk.hibernate.actions

import com.google.inject.Injector
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbEntity
import misk.hibernate.Operator
import misk.hibernate.Query
import misk.hibernate.ReflectionQuery
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.actions.HibernateDatabaseQueryMetadataFactory.Companion.QUERY_CONFIG_TYPE_NAME
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.checkQueryMatchesAction
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.findDatabaseQueryMetadata
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.getTransacterForDatabaseQueryAction
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.validateSelectPathsOrDefault
import misk.inject.typeLiteral
import misk.scope.ActionScoped
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.database.DatabaseQueryMetadata
import wisp.logging.getLogger
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Runs query from Database Query dashboard tab against DB and returns results */
@Singleton
internal class HibernateDatabaseQueryStaticAction @Inject constructor(
  @JvmSuppressWildcards private val callerProvider: ActionScoped<MiskCaller?>,
  private val databaseQueryMetadata: List<DatabaseQueryMetadata>,
  private val queries: List<HibernateQuery>,
  private val injector: Injector,
  private val queryLimitsConfig: ReflectionQuery.QueryLimitsConfig
) : WebAction {

  @Post(HIBERNATE_QUERY_STATIC_WEBACTION_PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun query(@RequestBody request: Request): Response {
    val caller = callerProvider.get()!!
    val queryClass = request.queryClass

    checkQueryMatchesAction(queryClass, false)

    val metadata = findDatabaseQueryMetadata(databaseQueryMetadata, queryClass)
    val transacter = getTransacterForDatabaseQueryAction(injector, metadata)

    val results = if (caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
      runStaticQuery(transacter, caller.principal, request, metadata)
    } else {
      throw UnauthorizedException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
    }

    return Response(results)
  }

  private fun runStaticQuery(
    transacter: Transacter,
    principal: String,
    request: Request,
    metadata: DatabaseQueryMetadata
  ) = transacter.transaction { session ->
    val (selectPaths, rows) = runStaticQuery(session, principal, request, metadata)
    rows.map { row ->
      // TODO (adrw) sort the map based on DbEntity order
      // TODO (adrw) Mirror this over to the static path
      row.mapIndexed { index, cell -> selectPaths[index] to cell }.toMap()
    }
  }

  private fun runStaticQuery(
    session: Session,
    principal: String,
    request: Request,
    metadata: DatabaseQueryMetadata
  ): Pair<List<String>, List<List<Any?>>> {
    val query = queries.map { it.query }.find { it.simpleName == metadata.queryClass }
      ?: throw BadRequestException("[query=${metadata.queryClass}] does not exist")
    val dbEntity = (
      (
        query.typeLiteral().getSupertype(
          Query::class.java
        ).type as ParameterizedType
        ).actualTypeArguments.first() as Class<DbEntity<*>>
      ).kotlin
    val maxRows =
      ((request.query[QUERY_CONFIG_TYPE_NAME] as Map<String, Any>?)?.get("maxRows") as Double?)
        ?.toInt() ?: queryLimitsConfig.maxMaxRows
    val configuredQuery = ReflectionQuery.Factory(queryLimitsConfig)
      .newQuery(query)
      .configureStatic(request, metadata, maxRows)

    val selectPaths = getStaticSelectPaths(request, metadata, dbEntity)
    logger.info(
      "Query sent from dashboard [principal=$principal]" +
        "[dbEntity=${request.entityClass}][selectPaths=$selectPaths] ${request.query}"
    )
    val rows = configuredQuery.dynamicList(session, selectPaths)
    return Pair(selectPaths, rows)
  }

  private fun getStaticSelectPaths(
    request: Request,
    metadata: DatabaseQueryMetadata,
    dbEntity: KClass<DbEntity<*>>
  ): List<String> {
    val selectMetadata: DatabaseQueryMetadata.SelectMetadata? =
      request.query.entries.firstOrNull { (key, _) ->
        key.split("/").first() == "Select"
      }?.let { (key, _) ->
        metadata.selects.find { it.parametersTypeName == key }
      }
    return validateSelectPathsOrDefault(
      dbEntity,
      selectMetadata?.paths
    )
  }

  private fun Query<out DbEntity<*>>.configureStatic(
    request: Request,
    metadata: DatabaseQueryMetadata,
    rowLimit: Int,
  ) = apply {
    maxRows = rowLimit
    request.query.forEach { (key, value) ->
      when (key.split("/").first()) {
        "Constraint" -> {
          metadata.constraints.find { it.parametersTypeName == key }?.let {
            dynamicAddConstraint(
              path = it.path,
              operator = Operator.valueOf(it.operator),
              value = (value as Map<String, String>)[it.name]
            )
          }
        }
        "Order" -> {
          metadata.orders.find { it.parametersTypeName == key }?.let {
            dynamicAddOrder(path = it.path, asc = it.ascending)
          }
        }
      }
    }
  }

  data class Request(
    val entityClass: String,
    val queryClass: String,
    /** Query request takes form of query field name (ie. MovieQuery.releaseDateAsc) to value (true) */
    val query: Map<String, Any>
  )

  data class Response(
    val results: List<Any>
  )

  companion object {
    private val logger = getLogger<HibernateDatabaseQueryStaticAction>()

    const val HIBERNATE_QUERY_STATIC_WEBACTION_PATH = "/api/database/query/hibernate/static"
  }
}
