package misk.web.dashboard

import jakarta.inject.Inject
import jakarta.inject.Singleton
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

/** Kotlin backed tab loader, equivalent to /_tab/slug/index.html */
@Singleton
class MiskWebTabIndexAction
@Inject
constructor(
  // TODO this probably shouldn't be limited to AdminDashboard tabs only but Misk-Web is
  //  deprecated so this can be solved if it's raised as a problem
  @AdminDashboard private val dashboardTabs: List<DashboardTab>
) : WebAction {
  @Get("$PATH/{slug}/{rest:.*}")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(@PathParam slug: String?): String {
    val dashboardTab =
      dashboardTabs.firstOrNull { slug == it.slug } ?: throw NotFoundException("No Misk-Web tab found for slug: $slug")
    val tabEntrypointJs = "/_tab/${dashboardTab.slug}/tab_${dashboardTab.slug}.js"

    return buildHtml {
      html {
        head {
          link {
            rel = "stylesheet"
            type = "text/css"
            href = "/@misk/common/styles.css"
          }
        }
        body {
          div {
            style = "padding: 2rem; margin-bottom: 6rem;"

            // App anchor
            div { id = dashboardTab.slug }
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
            src = tabEntrypointJs
          }
        }
      }
    }
  }

  companion object {
    const val PATH = "/api/dashboard/tab/misk-web"
  }
}
