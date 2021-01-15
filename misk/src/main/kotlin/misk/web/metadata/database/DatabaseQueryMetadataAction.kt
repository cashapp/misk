package misk.web.metadata.database

import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/** Display available queries and their types */
@Singleton
class DatabaseQueryMetadataAction @Inject constructor(
  val metadata: List<DatabaseQueryMetadata>
) : WebAction {

  @Get("/api/database/query/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(databaseQueryMetadata = metadata)
  }

  data class Response(
    val databaseQueryMetadata: List<DatabaseQueryMetadata>
  )
}

//Potentially input for max rows
//
//Constraint is a function, might have a parameter
//
//list of constraints, that you can keep adding
//some of them reveal another input field of type (string, int, datetime, list<datetime>, list<string>...)
//
//Potentially make the list just comma separated
//
//separate select to choose the projection
//
//Apply ACLs when Query is exposed for dashboard tab
//multibind<Query, AccessAnnotation>().to<MovieQuery>()
