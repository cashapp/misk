package misk.web.metadata.all

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.onChange
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.span
import misk.tailwind.components.AlertError
import misk.tailwind.components.AlertInfoHighlight
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout

@Singleton
internal class MetadataTabIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val allMetadataAction: AllMetadataAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(
    /** Metadata id to show. */
    @QueryParam q: String?
  ): String = dashboardPageLayout
    .newBuilder()
    .build { _, _, _ ->
      val allKeys = allMetadataAction.allKeys
      val metadata = allMetadataAction.getAll(q).all.values.firstOrNull()

      div("container mx-auto p-8") {
        val suffix = if (metadata != null) " / $q" else ""
        h1("text-3xl font-bold") { +"""Metadata$suffix""" }
        AlertInfoHighlight("Explore exposed metadata from different parts of your application.")

        div {
          form {
            action = PATH
            select("mt-2 block w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6") {
              id = "q"
              name = "q"
              onChange = "this.form.submit()"

              // Blank first option on load
              option {
                value = ""
                +""
              }

              allKeys.forEach { key ->
                option {
                  if (q == key) {
                    selected = true
                  }
                  value = key
                  +key
                }
              }
            }
          }

          if (metadata == null && q?.isNotBlank() == true) {
            AlertError("Metadata '${q}' not found. Please select a valid metadata id from the select dropdown above.")
          }

          if (q != null && metadata != null) {
            h3("mb-4 text-color-blue-500") {
              code {
                span("font-bold") {
                  +"GET "
                }
                a(classes = "text-blue-500 hover:underline") {
                  href = AllMetadataAction.PATH.replace("{id}", q)
                  +AllMetadataAction.PATH.replace("{id}", q)
                }
              }
            }

            div("my-4") {
              metadata.descriptionBlock(this@build)
            }

            metadata.contentBlock(this@build)
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/metadata/"
  }
}
