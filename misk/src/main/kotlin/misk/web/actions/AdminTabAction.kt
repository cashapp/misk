package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminTabAction : WebAction {
  @Inject lateinit var registeredTabs: List<AdminTab>

  @Get("/api/_admin/loader/all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(tabs = registeredTabs.map { tab -> tab.slug to tab }.toMap())
  }

  data class Response(val tabs: Map<String, AdminTab>)
}

data class AdminTab(
  val name: String,
  val slug: String,
  val url_path_prefix: String,
  val icon: String = "widget-button"
) {
  init {
    require(name.first().isUpperCase() &&
        slug.filter { char -> !char.isUpperCase()
            && !char.isWhitespace() }.length.equals(slug.length) &&
        icon.filter { char -> !char.isUpperCase()
            && !char.isWhitespace() }.length.equals(icon.length) &&
        url_path_prefix.startsWith("/") &&
        url_path_prefix.endsWith("/"))
  }
}