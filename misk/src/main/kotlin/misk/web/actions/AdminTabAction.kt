package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminTabAction : WebAction {
  @Inject lateinit var registeredTabs: List<RegisteredTab>

  @Get("/api/_admin/loader/all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(tabs = registeredTabs.map{ tab -> tab.name to tab }.toMap())
  }

  data class Response(val tabs: Map<String, RegisteredTab>)
}

data class RegisteredTab(
  val urlPathPrefix: String,
  val upstreamBaseUrl: HttpUrl,
  val name: String,
  val slug: String,
  val icon: String,
  val path_jar: String
)