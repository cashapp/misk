package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Admin Tab Action
 *
 * Returns all Admin Tabs for primary use in dynamically building the menu bar of the /_admin dashboard
 *
 * Guidelines
 * - name should start with a capital letter unless it is a proper noun (ie. iOS)
 * - slug must be valid slug (lowercase and no white space)
 * - icon must be valid slug (lowercase and no white space).
 *      - Not enforced in Misk but icon must also be a valid slug in BlueprintJS/Icons
 * - url_path_prefix must start and end with "/"
 *
 */

@Singleton
class AdminTabAction : WebAction {
  @Inject lateinit var registeredTabs: List<AdminTab>

  @Get("/api/admintab/all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(adminTabs = registeredTabs.map { tab -> tab.slug to tab }.toMap())
  }

  data class Response(val adminTabs: Map<String, AdminTab>)
}

data class AdminTab(
  val name: String,
  val slug: String,
  val url_path_prefix: String,
  val icon: String = "widget-button"
) {
  init {
    require(slug.filter { char ->
      !char.isUpperCase()
          && !char.isWhitespace()
    }.length.equals(slug.length) &&
        icon.filter { char ->
          !char.isUpperCase()
              && !char.isWhitespace()
        }.length.equals(icon.length) &&
        url_path_prefix.matches(Regex("(/[^/]+)*")) &&
        !url_path_prefix.startsWith("/api"))
  }
}