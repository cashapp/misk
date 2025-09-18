package misk.cron

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.onChange
import kotlinx.html.option
import kotlinx.html.select
import misk.moshi.adapter
import misk.tailwind.Link
import misk.tailwind.components.AlertError
import misk.tailwind.components.AlertInfo
import misk.tailwind.components.AlertInfoHighlight
import misk.tailwind.components.AlertSuccess
import misk.tailwind.components.CodeBlock
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.toFormattedJson
import misk.web.v2.DashboardPageLayout
import wisp.moshi.defaultKotlinMoshi

@Singleton
internal class CronTabIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val cronManager: CronManager,
) : WebAction {
  val adapter = defaultKotlinMoshi
    .adapter<CronManager.CronEntry.Metadata>()

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(
    /** Cron name to show. */
    @QueryParam q: String?,
    /** Alert message to show in banner. */
    @QueryParam m: String?,
  ): String = dashboardPageLayout
    .newBuilder()
    .build { _, _, _ ->
      val runningCrons = cronManager.getRunningCrons()
      val allCrons = cronManager.getCronEntries()
      val cron = allCrons[q]

      div("container mx-auto p-8") {
        val suffix = if (cron != null) " / $q" else ""
        h1("text-3xl font-bold") { +"""Cron$suffix""" }

        AlertInfoHighlight(
          "Explore and control your application's cron jobs.",
          Link(
            label = "${runningCrons.size} running / ${allCrons.size} registered",
            href = PATH,
            isPageNavigation = true
          ),
        )

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

              allCrons.forEach { cron ->
                option {
                  if (q == cron.key) {
                    selected = true
                  }
                  value = cron.key
                  +cron.key
                }
              }
            }
          }

          if (cron == null && q?.isNotBlank() == true) {
            AlertError("Cron '${q}' not found. Please select a valid cron name from the select dropdown above.")
          }

          if (m != null) {
            AlertSuccess(m, Link("Clear", CronTabIndexAction.path(q)))
          }

          if (q != null && cron != null) {
            h3("text-xl font-bold my-4") { +"""Controls""" }
            AlertInfo("Run cron ${cron.name}", Link("Run Now", CronTabRunAction.path(cron.name)))

            val metadata = cron.toMetadata()
            val prettyPrint = adapter
              .toFormattedJson(metadata)

            h3("text-xl font-bold my-4") { +"""Metadata""" }
            CodeBlock(prettyPrint)
          }

          if (q.isNullOrBlank() && runningCrons.isNotEmpty()) {
            h3("text-xl font-bold my-4") { +"""Running Crons""" }

            runningCrons.forEach {
              val cronName = it.entry.name
              val instanceId = it.completableFuture.toString().removePrefix("java.util.concurrent.CompletableFuture")
              AlertInfo("${cronName} ($instanceId) is running", Link("Info", CronTabIndexAction.path(cronName)))
            }
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/cron/"
    fun path(name: String?, message: String? = null) =
      "/_admin/cron/" + if (name.isNullOrBlank()) {
        "?"
      } else {
        "?q=$name"
      } + (message?.let { "&m=$message" } ?: "")
  }
}
