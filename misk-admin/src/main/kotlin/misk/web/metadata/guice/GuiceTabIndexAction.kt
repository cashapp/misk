package misk.web.metadata.guice

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.role
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.unsafe
import misk.tailwind.components.AlertInfoHighlight
import misk.tailwind.components.ToggleContainer
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon
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
      style {
        unsafe {
          raw("""
            .tooltip {
              visibility: hidden;
            }
            
            .has-tooltip:hover .tooltip {
              opacity: 1;
              visibility: visible;
            }
          """.trimIndent())
        }
      }
    }
    .build { _, _, _ ->
      val registrations = guiceMetadataProvider.get().guice.bindingMetadata

      div("container mx-auto p-8") {
        h1("text-3xl font-bold mb-4") {
          +"""Guice"""
        }
        AlertInfoHighlight(
          "Explore the dependency injection Guice bindings for your application.",
          "Guice Docs",
          "https://github.com/google/guice/wiki/GettingStarted"
        )

        div {
          attributes["data-controller"] = "search-bar"

          div {
            input(
              type = InputType.search,
              classes = "flex h-10 w-full bg-gray-100 hover:bg-gray-200 duration-500 border-none rounded-lg text-sm"
            ) {
              attributes["data-action"] = "input->search-bar#search"
              placeholder =
                "Search dependency injection types like AccessAnnotationEntry, DataSourceService, or RealTokenGenerator..."
            }
          }

          div("lg:col-start-3 lg:row-end-1 py-4") {
            registrations
              .groupBy { it.typePackage }
              .toSortedMap()
              .map { (typePackage, registration) ->
                div("registration-group") {
                  ToggleContainer(
                    buttonText = typePackage,
                    classes = "py-2",
                    borderless = true,
                    marginless = true,
                  ) {
                    div("grid grid-cols-1 gap-y-4") {
                      registration.map { BindingCard(it) }
                    }
                  }
                }
              }
          }
        }
      }
    }

  private fun TagConsumer<*>.BindingCard(binding: GuiceMetadataProvider.BindingMetadata) {
    val truncateLength = 85

    div("registration mt-5 rounded-lg bg-gray-50 shadow-sm ring-1 ring-gray-900/5") {
      dl("flex flex-wrap py-6") {
        div("flex-auto pl-6 pb-6") {
          this@dl.dd("mt-1 text-base font-mono font-semibold leading-6 text-gray-900") { +binding.type }
        }
        div("flex-none self-end px-6 pb-6") {
          if (!binding.annotation.isNullOrBlank()) {
            CardTag("Annotation", "blue") { +binding.annotation!! }
          }
          if (!binding.scope.isNullOrBlank()) {
            CardTag("Scope", "green") { +binding.scope!! }
          }
        }
        if (binding.source.isNotBlank() && binding.source != "[unknown source]") {
          val source = if (binding.source.length > truncateLength) {
            val leadingLength = 32
            binding.source.take(leadingLength) + "..." + binding.source.takeLast(truncateLength - leadingLength)
          } else {
            binding.source
          }
          CardRow("Source", Heroicons.OUTLINE_ARROW_TOP_RIGHT_ON_SQUARE) {
            val sourceUrl = guiceSourceUrlProvider.urlForSource(binding.source)
            if (sourceUrl != null) {
              a(
                classes = "underline text-gray-500 hover:text-gray-900",
                href = sourceUrl,
                target = "_blank"
              ) { +source }
            } else {
              +source
            }
          }
        }
        if (binding.provider.isNotBlank()) {
          CardRow("Provider", Heroicons.OUTLINE_CODE_BRACKET_SQUARE) {
            if (binding.provider.length < truncateLength) {
              +binding.provider
            } else {
              ToggleContainer(
                buttonText = binding.provider.take(truncateLength) + "...",
                borderless = true,
                marginless = true,
              ) {
                div("py-6 font-mono") {
                  if (binding.provider.split(" ").any { it.length > truncateLength }) {
                    // Add some spaces so text can wrap for long package names and other cases
                    +binding.provider
                      .split("[").joinToString("[ ") { it }
                      .split("(").joinToString("( ") { it }
                      .split("$").joinToString("$ ") { it }
                      .split("Provider<").joinToString("Provider< ") { it }
                  } else {
                    +binding.provider
                  }
                }
              }
            }
          }
        }
      }

      if (!binding.subElements.isNullOrEmpty()) {
        CardRow("Binder Elements", Heroicons.OUTLINE_QUEUE_LIST) {
          ToggleContainer(
            buttonText = "Binder Elements",
            borderless = true,
            marginless = true,
          ) {
            binding.subElements!!.map { BindingCard(it) }
          }
        }
      }
    }
  }

  private fun TagConsumer<*>.CardTag(
    label: String,
    color: String,
    content: TagConsumer<*>.() -> Unit
  ) {
    dt("sr-only") { +label }
    dd("inline-flex items-center rounded-md bg-$color-50 px-2 py-1 text-xs font-medium text-$color-700 ring-1 ring-inset ring-$color-600/20") {
      content()
    }
  }

  private fun TagConsumer<*>.Tooltip(tooltip: String, content: TagConsumer<*>.() -> Unit) {
    div("has-tooltip") {
      content()
      div("tooltip absolute z-10 invisible inline-block px-3 py-2 text-sm font-medium text-white transition-opacity duration-300 bg-gray-900 rounded-lg shadow-sm opacity-0 tooltip dark:bg-gray-700") {
        id = "tooltip-default"
        role = "tooltip"
        +tooltip
      }
    }
  }

  private fun TagConsumer<*>.CardRow(
    label: String,
    icon: Heroicons,
    content: TagConsumer<*>.() -> Unit
  ) {
    div("flex w-full flex-none gap-x-4 border-t border-gray-900/5 px-6 py-6") {
      dt("flex-none") {
        span("sr-only") { +label }
        Tooltip(label) {
          heroicon(icon)
        }
      }
      dd("text-sm font-medium leading-6 text-gray-900") {
        content()
      }
    }
  }

  companion object {
    const val PATH = "/_admin/guice/"
  }
}
