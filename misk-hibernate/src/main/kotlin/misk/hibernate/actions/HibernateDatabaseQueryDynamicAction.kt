package misk.hibernate.actions

import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.reflect.KClass
import misk.MiskCaller
import misk.audit.AuditRequestResponse
import misk.exceptions.BadRequestException
import misk.exceptions.ForbiddenException
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
import misk.web.mediatype.MediaTypes
import misk.web.metadata.database.DatabaseQueryMetadata

@Singleton
internal class HibernateDatabaseQueryDynamicAction @Inject constructor(
  @JvmSuppressWildcards private val callerProvider: ActionScoped<MiskCaller?>,
  private val databaseQueryMetadata: List<DatabaseQueryMetadata>,
  private val injector: Injector,
  private val queryLimitsConfig: ReflectionQuery.QueryLimitsConfig,
) : WebAction {

  @Post(HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  @AuditRequestResponse
  fun query(@RequestBody request: Request): Response {
    val caller = callerProvider.get()
      ?: throw ForbiddenException("Authentication required")

    checkQueryMatchesAction(request.queryClass, isDynamicAction = true)
    val metadata = findDatabaseQueryMetadata(databaseQueryMetadata, request.queryClass)
    val transacter = getTransacterForDatabaseQueryAction(injector, metadata)

    authorizeCaller(caller, metadata)
    val dbEntity = resolveAndValidateEntity(transacter, request, metadata)
    val results = executeQueryInTransaction(transacter, caller.principal, dbEntity, request)
    return Response(results)
  }

  private fun authorizeCaller(caller: MiskCaller, metadata: DatabaseQueryMetadata) {
    if (!caller.isAllowed(metadata.allowedCapabilities, metadata.allowedServices)) {
      throw ForbiddenException("Unauthorized to query [dbEntity=${metadata.entityClass}]")
    }
  }

  private fun resolveAndValidateEntity(
    transacter: Transacter,
    request: Request,
    metadata: DatabaseQueryMetadata,
  ): KClass<out DbEntity<*>> {
    val dbEntity = transacter.entities().find { it.simpleName == request.entityClass }
      ?: throw BadRequestException("Unknown entity class")
    
    if (dbEntity.simpleName != metadata.entityClass) {
      throw ForbiddenException("Requested entity does not match authorized query class")
    }
    return dbEntity
  }

  private fun executeQueryInTransaction(
    transacter: Transacter,
    principal: String,
    dbEntity: KClass<out DbEntity<*>>,
    request: Request,
  ): List<Map<String, Any?>> =
    transacter.transaction { session ->
      val (selectPaths, rawRows) = buildAndExecuteQuery(session, principal, dbEntity, request)
      mapRowsToResults(selectPaths, rawRows)
    }

  private fun buildAndExecuteQuery(
    session: Session,
    principal: String,
    dbEntity: KClass<out DbEntity<*>>,
    request: Request,
  ): QueryResult {
    // FIX: Enforce ceiling for maxRows to prevent DoS
    val requestedMaxRows = request.query.queryConfig?.maxRows ?: queryLimitsConfig.maxMaxRows
    val effectiveMaxRows = minOf(requestedMaxRows, queryLimitsConfig.maxMaxRows)

    val query = ReflectionQuery.Factory(queryLimitsConfig)
      .dynamicQuery(dbEntity)
      .applyDynamicConfiguration(request, effectiveMaxRows)

    val selectPaths = validateSelectPathsOrDefault(dbEntity, request.query.select?.paths)
    logger.info { "Dynamic query executed [principal=$principal][dbEntity=${dbEntity.simpleName}]" }
    
    val rows = query.dynamicList(session, selectPaths)
    return QueryResult(selectPaths, rows)
  }

  private fun Query<out DbEntity<*>>.applyDynamicConfiguration(
    request: Request,
    rowLimit: Int,
  ) = apply {
    maxRows = rowLimit
    request.query.constraints?.forEach { constraint ->
      dynamicAddConstraint(
        path = constraint.path ?: throw BadRequestException("Constraint path required"),
        operator = constraint.operator ?: throw BadRequestException("Constraint operator required"),
        value = constraint.value,
      )
    }
    request.query.orders?.forEach { order ->
      dynamicAddOrder(
        path = order.path ?: throw BadRequestException("Order path required"),
        asc = order.ascending ?: throw BadRequestException("Order direction required")
      )
    }
  }

  private fun mapRowsToResults(
    selectPaths: List<String>,
    rawRows: List<List<Any?>>,
  ): List<Map<String, Any?>> =
    rawRows.map { row ->
      row.mapIndexed { index, cell -> selectPaths[index] to cell }.toMap()
    }

  data class Request(
    val entityClass: String,
    val queryClass: String,
    val query: HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery,
  )

  data class Response(val results: List<Map<String, Any?>>)

  private data class QueryResult(val selectPaths: List<String>, val rows: List<List<Any?>>)

  companion object {
    private val logger = getLogger<HibernateDatabaseQueryDynamicAction>()
    const val HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH = "/api/database/query/hibernate/dynamic"
  }
}