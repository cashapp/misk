package misk.hibernate.actions

import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.reflect.KClass
import misk.MiskCaller
import misk.audit.AuditRequestResponse
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.hibernate.ReflectionQuery
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.checkQueryMatchesAction
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.findDatabaseQueryRegistration
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
import misk.web.mediatype.MediaTypes

/** Runs query from Database Query dashboard tab against DB and returns results */
@Singleton
internal class HibernateDatabaseQueryDynamicAction
@Inject
constructor(
  @JvmSuppressWildcards private val callerProvider: ActionScoped<MiskCaller?>,
  private val databaseQueryRegistrations: List<HibernateDatabaseQueryRegistration>,
  private val injector: Injector,
  private val queryLimitsConfig: ReflectionQuery.QueryLimitsConfig,
) : WebAction {

  @Post(HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  @AuditRequestResponse
  fun query(@RequestBody request: Request): Response {
    val caller = callerProvider.get()!!
    val queryClass = request.queryClass

    checkQueryMatchesAction(queryClass, true)

    val registration = findDatabaseQueryRegistration(databaseQueryRegistrations, queryClass)
    val metadata = registration.metadata
    val transacter = getTransacterForDatabaseQueryAction(injector, registration.entityClass)

    val results =
      if (caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
        runDynamicQuery(transacter, caller.principal, request, registration)
      } else {
        throw UnauthorizedException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
      }

    return Response(results)
  }

  private fun runDynamicQuery(
    transacter: Transacter,
    principal: String,
    request: Request,
    registration: HibernateDatabaseQueryRegistration,
  ) =
    transacter.transaction { session ->
      // Query the entity the authorized registration was built from. Reject a request naming a different
      // entity than the authorized query class so the caller gets a clear error.
      if (request.entityClass != registration.metadata.entityClass) {
        throw UnauthorizedException(
          "Requested entity [dbEntity=${request.entityClass}] does not match authorized query [queryClass=${request.queryClass}]"
        )
      }
      val dbEntity = registration.entityClass
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
    val configuredQuery =
      ReflectionQuery.Factory(queryLimitsConfig).dynamicQuery(dbEntity).configureDynamic(request, maxRows)
    val selectPaths = validateSelectPathsOrDefault(dbEntity, request.query.select?.paths)
    logger.info(
      "Query sent from dashboard [principal=$principal]" +
        "[dbEntity=${request.entityClass}][selectPaths=$selectPaths] ${request.query}"
    )
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

  data class Response(val results: List<Any>)

  companion object {
    private val logger = getLogger<HibernateDatabaseQueryDynamicAction>()

    const val HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH = "/api/database/query/hibernate/dynamic"
  }
}
