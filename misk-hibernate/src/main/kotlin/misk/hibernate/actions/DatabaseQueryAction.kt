package misk.hibernate.actions

import misk.hibernate.Query
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/** Runs query against DB and returns results */
@Singleton
class DatabaseQueryAction @Inject constructor(
  val queries: List<Query<*>>
) : WebAction {

  @Post("/api/database/query/hibernate/execute")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(request: Request): Response {
    TODO()
  }

  data class Request(
    val table: String,
    /** @Constraint functions on Misk Query interface */
    val constraints: Map<String, Any>,
    /** @Order functions on Misk Query interface */
    val orders: Map<String, Any>,
    /** @Select functions on Misk Query interface */
    val select: String
  )

  data class Response(
    val headers: List<String>,
    val rows: List<List<String>>
  )
}
