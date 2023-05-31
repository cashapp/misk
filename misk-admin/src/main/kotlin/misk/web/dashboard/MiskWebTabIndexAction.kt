package misk.web.dashboard

import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.style
import misk.exceptions.NotFoundException
import misk.hotwire.buildHtml
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin backed tab loader, equivalent to /_tab/slug/index.html
 */
@Singleton
class MiskWebTabIndexAction @Inject constructor(
  @AdminDashboard private val dashboardTabs: List<DashboardTab>,
) : WebAction {
  @Get("$PATH/{slug}")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(@PathParam slug: String?): String {
    val dashboardTab = dashboardTabs.firstOrNull { slug == it.slug }
    return buildHtml {
      html {
        head {
          dashboardTab?.let {
            link {
              rel = "stylesheet"
              type = "text/css"
              href = "/@misk/common/styles.css"
            }
          }
        }
        body {
          // TODO remove this hack when new Web Actions tab lands and old ones removed, v1 and v2 are in the same web-actions tab
          val normalizedSlug = if (slug == "web-actions-v1") "web-actions" else slug ?: "null"

          div {
            style = "padding: 2rem; margin-bottom: 6rem;"

            // App anchor
            div {
              id = normalizedSlug
            }

            // 404
            dashboardTab
              ?: throw NotFoundException("No tab found for slug: $normalizedSlug")
          }

          // Common resources
          script {
            type = "text/javascript"
            src = "/@misk/common/vendors.js"
          }
          script {
            type = "text/javascript"
            src = "/@misk/common/common.js"
          }
          script {
            type = "text/javascript"
            src = "/@misk/core/core.js"
          }
          script {
            type = "text/javascript"
            src = "/@misk/simpleredux/simpleredux.js"
          }

          // Tab specific resources
          script {
            type = "text/javascript"
            src = "/_tab/${normalizedSlug}/tab_${normalizedSlug}.js"
          }
        }
      }
    }
  }

  companion object {
    const val PATH = "/api/dashboard/tab/misk-web"
  }
}
