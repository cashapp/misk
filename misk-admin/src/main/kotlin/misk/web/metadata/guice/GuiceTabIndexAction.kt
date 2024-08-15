package misk.web.metadata.guice

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.input
import kotlinx.html.script
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout

@Singleton
class GuiceTabIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val guiceMetadataProvider: GuiceMetadataProvider,
  private val guiceSourceUrlProvider: GuiceSourceUrlProvider,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .headBlock {
      val controllers = listOf(
        "search_bar_controller",
      )
      controllers.forEach {
        script {
          type = "module"
          src = "/static/controllers/$it.js"
        }
      }
    }
    .build { _, _, _ ->
      val registrations = guiceMetadataProvider.get().guice.bindingMetadata

      val checkIcon = "✔"

      div("p-4 sm:p-6 lg:p-8") {
        h1("text-3xl font-medium mb-8") {
          +"""Guice"""
        }
        div {
          attributes["data-controller"] = "search-bar"
          div {
            input(
              type = InputType.search,
              classes = "flex h-10 w-full bg-gray-100 hover:bg-gray-200 duration-500 border-none rounded-lg text-sm"
            ) {
              attributes["data-action"] = "input->search-bar#search"
              placeholder = "Search"
            }
          }
          div("mt-8 flow-root") {
            div("-mx-4 -my-2 overflow-x-auto sm:-mx-6 lg:-mx-8") {
              div("inline-block min-w-full py-2 align-middle sm:px-6 lg:px-8") {
                table("min-w-full divide-y divide-gray-300") {
                  thead {
                    tr {
                      th(classes = "px-3 py-3.5 text-left text-sm font-semibold text-gray-900") {
                        +"""Type"""
                      }
                      th(classes = "px-3 py-3.5 text-left text-sm font-semibold text-gray-900") {
                        +"""Source"""
                      }
                      th(classes = "px-3 py-3.5 text-left text-sm font-semibold text-gray-900") {
                        +"""Annotation"""
                      }
                      th(classes = "px-3 py-3.5 text-left text-sm font-semibold text-gray-900") {
                        +"""Scope"""
                      }
                      th(classes = "px-3 py-3.5 text-left text-sm font-semibold text-gray-900") {
                        +"""Provider"""
                      }
                    }
                  }
                  tbody("divide-y divide-gray-200") {
                    registrations.map {
                      tr("registration") {
                        td("whitespace-normal px-3 py-4 text-sm") {
                          +it.type
                        }
                        td("whitespace-normal px-3 py-4 text-sm") {
                          val sourceUrl = guiceSourceUrlProvider.urlForSource(it.source)
                          if (sourceUrl != null) {
                            a(
                              classes = "underline text-gray-500 hover:text-gray-900",
                              href = sourceUrl,
                              target = "_blank"
                            ) { +it.source }
                          } else {
                            +it.source
                          }
                        }
                        td(
                          "whitespace-normal px-3 py-4 text-sm"
                        ) { + (it.annotation ?: "") }
                        td("whitespace-normal px-3 py-4 text-sm") {
                          + (it.scope ?: "")
                        }
                        td("whitespace-normal px-3 py-4 text-sm") {
                          +it.provider
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/guice/"
  }
}
