package misk.web.metadata.all

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.onChange
import kotlinx.html.option
import kotlinx.html.pre
import kotlinx.html.select
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.Metadata
import misk.web.v2.DashboardPageLayout

@Singleton
class AllMetadataTabAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val allMetadata: Map<String, Metadata>,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(
    /** Metadata id to show. */
    @QueryParam q: String?
  ): String = dashboardPageLayout
    .newBuilder()
    .build { appName, _, _ ->
      val metadata = q?.let { allMetadata[it] }?.metadata?.toString()
        // TODO properly optimistically serialize to JSON
        ?.split("),")?.joinToString("),\n")
        ?.split(",")?.joinToString(",\n")

      div("container mx-auto p-8") {
        h1 { +"""All Metadata""" }

        div {
          form {
            action = "/_admin/metadata/"
            select("mt-2 block w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6") {
              id = "q"
              name = "q"
              onChange = "this.form.submit()"

              allMetadata.keys.sorted().forEachIndexed { index, key ->
                option {
                  if ((q == null && index == 0) || q == key) {
                    selected = true
                  }
                  value = key
                  +key
                }
              }
            }
          }
        }

        // code pre of pretty metadata json
        if (metadata != null || allMetadata[q] != null) {
          pre {
            code("text-wrap font-mono") {
              +(metadata ?: "Metadata not found for $q")
            }
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/metadata/"

  }
}
