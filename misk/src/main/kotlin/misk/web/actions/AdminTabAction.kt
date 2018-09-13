package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import misk.web.resources.ResourceEntryCommon
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
 * - icon must be valid slug (lowercase and no white space)
 *      - Not enforced in Misk but icon must also be a valid slug in BlueprintJS/Icons
 * - url_path_prefix must start and end with "/"
 *
 */

@Singleton
class AdminTabAction : WebAction {
  @Inject lateinit var registeredTabs: List<AdminTab>

  @Get("/api/admintabs")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  @Suppress("UNUSED_PARAMETER")
  fun getAll(): Response {
    return Response(adminTabs = registeredTabs.map { tab -> tab.slug to tab }.toMap(), adminTabCategories = registeredTabs.sortedBy { it.slug }.groupBy { it.category }.toSortedMap())
  }

  data class Response(val adminTabs: Map<String, AdminTab>, val adminTabCategories: Map<String, List<AdminTab>>)
}

data class AdminTab(
  val name: String,
  val slug: String,
  val url_path_prefix: String,
  val category: String = "Other"
) {
  init {
    // Requirements enforce the guidelines outlined at top of the file
    // TODO(adrw) see if there's a more readable way to do these requirements
    ResourceEntryCommon.requireValidUrlPathPrefix(url_path_prefix)
    require(slug.filter { char -> !char.isUpperCase() && !char.isWhitespace() }.length == slug.length)
  }
}