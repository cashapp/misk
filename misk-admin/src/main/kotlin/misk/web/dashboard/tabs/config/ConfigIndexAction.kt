package misk.web.dashboard.tabs.config

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.pre
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.v2.DashboardPageLayout

@Singleton
class ConfigIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val configMetadataAction: ConfigMetadataAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .build { _, _, _ ->
      div("center container p-8") {
        form {
          div("space-y-12") {
            div("border-b border-gray-900/10 pb-12") {
              // TODO make this font bigger
              h1("text-base text-2xl font-bold leading-7 text-gray-900") { +"""Config""" }

              val resources = configMetadataAction.getAll().resources
              resources.map { (title, contents) ->
                div("py-5") {
                  h2("text-base text-xl font-bold leading-7 text-gray-900") { +title }
                  // TODO change to code block with word wrap
                  pre("text-sm wrap") { +"""$contents""" }
                }
              }

              // TODO add documentation dropdown section
            }
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/config/"
  }
}
