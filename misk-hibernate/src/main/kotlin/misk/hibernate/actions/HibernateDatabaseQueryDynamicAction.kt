package misk.hibernate.actions

import com.google.inject.Injector
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.hibernate.ReflectionQuery
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.checkQueryMatchesAction
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.findDatabaseQueryMetadata
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.getTransacterForDatabaseQueryAction
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.validateSelectPathsOrDefault
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.interceptors.LogRequestResponse
import misk.web.mediatype.MediaTypes
import misk.web.metadata.database.DatabaseQueryMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Runs query from Database Query dashboard tab against DB and returns results */
@Singleton
internal class HibernateDatabaseQueryDynamicAction @Inject constructor(
  @JvmSuppressWildcards private val callerProvider: ActionScoped<MiskCaller?>,
  private val databaseQueryMetadata: List<DatabaseQueryMetadata>,
  private val injector: Injector,
  private val queryLimitsConfig: ReflectionQuery.QueryLimitsConfig
) : WebAction {

  @Post(HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun query(@RequestBody request: Request): Response {
    val caller = callerProvider.get()!!
    val queryClass = request.queryClass

    checkQueryMatchesAction(queryClass, true)
    logger.info("Query sent from dashboard [principal=$caller][dbEntity=${request.entityClass}] ${request.query}")

    val metadata = findDatabaseQueryMetadata(databaseQueryMetadata, queryClass)
    val transacter = getTransacterForDatabaseQueryAction(injector, metadata)

    val results = if (caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
      runDynamicQuery(transacter, caller.principal, request, metadata)
    } else {
      throw UnauthorizedException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
    }

    return Response(results)
  }

  private fun runDynamicQuery(
    transacter: Transacter,
    principal: String,
    request: Request,
    metadata: DatabaseQueryMetadata
  ) = transacter.transaction { session ->
    val dbEntity = transacter.entities().find { it.simpleName == request.entityClass }
      ?: throw BadRequestException(
        "[dbEntity=${metadata.entityClass}] is not an installed HibernateEntity"
      )
    val (selectPaths, rows) = runDynamicQuery(session, principal, dbEntity, request)
    rows.map { row ->
      // TODO (adrw) sort the map based on DbEntity order
      // TODO (adrw) Mirror this over to the static path
      row.mapIndexed { index, cell -> selectPaths[index] to cell }.toMap()
    }
  }

  private fun runDynamicQuery(
    session: Session,
    principal: String,
    dbEntity: KClass<out DbEntity<*>>,
    request: Request,
  ): Pair<List<String>, List<List<Any?>>> {
    val maxRows = request.query.queryConfig?.maxRows ?: queryLimitsConfig.maxMaxRows
    val configuredQuery = ReflectionQuery.Factory(queryLimitsConfig)
      .dynamicQuery(dbEntity)
      .configureDynamic(request, maxRows)
    val selectPaths = validateSelectPathsOrDefault(dbEntity, request.query.select?.paths)
    logger.info("Query sent from dashboard [principal=$principal][dbEntity=${request.entityClass}][selectPaths=$selectPaths] ${request.query}")
    val rows = configuredQuery.dynamicList(session, selectPaths)
    return Pair(selectPaths, rows)
  }

  private fun Query<out DbEntity<*>>.configureDynamic(request: Request, rowLimit: Int) = apply {
    maxRows = rowLimit
    request.query.constraints?.forEach { (path, operator, value) ->
      if (path == null) throw BadRequestException("Constraint path must be non-null")
      if (operator == null) throw BadRequestException("Constraint operator must be non-null")
      dynamicAddConstraint(path = path, operator = operator, value = value)
    }
    request.query.orders?.forEach { (path, ascending) ->
      if (path == null) throw BadRequestException("Order path must be non-null")
      if (ascending == null) throw BadRequestException("Order ascending must be non-null")
      dynamicAddOrder(path = path, asc = ascending)
    }

  }

  data class Request(
    val entityClass: String,
    val queryClass: String,
    val query: HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery,
  )

  data class Response(
    val results: List<Any>
  )

  companion object {
    private val logger = getLogger<HibernateDatabaseQueryDynamicAction>()

    const val HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH = "/api/database/query/hibernate/dynamic"
  }
}
