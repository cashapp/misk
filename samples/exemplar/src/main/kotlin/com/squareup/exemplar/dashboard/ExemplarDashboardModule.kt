package com.squareup.exemplar.dashboard

import com.squareup.exemplar.dashboard.admin.AlphaIndexAction
import com.squareup.exemplar.dashboard.frontend.EcommerceLandingPage
import com.squareup.exemplar.dashboard.frontend.GraphD3JsPage
import com.squareup.exemplar.dashboard.frontend.IndexPage
import com.squareup.exemplar.dashboard.frontend.SimplePage
import com.squareup.exemplar.dashboard.support.SupportBravoIndexAction
import com.squareup.exemplar.dashboard.support.SupportDashboardIndexAction
import jakarta.inject.Qualifier
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul
import kotlinx.html.unsafe
import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.AdminDashboardModule
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardModule
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.DashboardTheme
import misk.web.dashboard.EnvironmentToColorLookup
import misk.web.dashboard.MiskWebColor
import misk.web.dashboard.MiskWebTheme
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import misk.web.v2.DashboardIndexAccessBlock
import misk.web.v2.DashboardIndexBlock

class ExemplarDashboardModule : KAbstractModule() {
  override fun configure() {
    // Favicon.ico and any other shared static assets available at /static/*
    multibind<StaticResourceEntry>()
      .toInstance(
        StaticResourceEntry(
          url_path_prefix = "/static/",
          resourcePath = "classpath:/web/static/"
        )
      )
    install(WebActionModule.createWithPrefix<StaticResourceAction>(url_path_prefix = "/static/"))

    // Custom Frontend at /app/
    install(WebActionModule.create<EcommerceLandingPage>())
    install(WebActionModule.create<GraphD3JsPage>())
    install(WebActionModule.create<SimplePage>())
    install(WebActionModule.create<IndexPage>())

    // Custom Support Dashboard /support/
    install(WebActionModule.create<SupportDashboardIndexAction>())
    install(WebActionModule.create<SupportBravoIndexAction>())
    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<SupportDashboard>("/support/")
    )
    install(
      DashboardModule.createHotwireTab<SupportDashboard, SupportDashboardAccess>(
        slug = "bravo",
        urlPathPrefix = "/support/bravo/",
        menuLabel = "Bravo",
        menuCategory = "Support"
      )
    )
    install(
      DashboardModule.createMenuLink<SupportDashboard, SupportDashboardAccess>(
        label = "Admin Dashboard",
        url = "/_admin/",
      )
    )
    install(
      DashboardModule.createMenuLink<SupportDashboard, SupportDashboardAccess>(
        label = "Cash App",
        url = "https://cash.app/",
      )
    )

    // Admin Dashboard Setup
    install(
      AdminDashboardModule(
        isDevelopment = true,
        configTabMode = ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG,
      )
    )

    bind<DashboardTheme>().toInstance(
      DashboardTheme<AdminDashboard>(
        MiskWebTheme(
          bannerLinkHover = MiskWebColor.RED.hexColor,
          bannerText = MiskWebColor.RED.hexColor,
          button = MiskWebColor.RED.hexColor,
          buttonHover = MiskWebColor.RED.hexColor,
          categoryText = MiskWebColor.RED.hexColor,
          environmentToColor = EnvironmentToColorLookup(
            default = MiskWebColor.RED.hexColor,
            DEVELOPMENT = MiskWebColor.RED.hexColor,
            TESTING = MiskWebColor.RED.hexColor,
            STAGING = MiskWebColor.RED.hexColor,
            PRODUCTION = MiskWebColor.RED.hexColor,
          ),
          navbarBackground = MiskWebColor.RED.hexColor,
          navbarLinkHover = MiskWebColor.RED.hexColor,
          navbarText = MiskWebColor.RED.hexColor,
        )
      )
    )

    val dashboardIndexAccessBlock = DashboardIndexAccessBlock<AdminDashboard> { appName: String, caller: MiskCaller?, authenticatedTabs: List<DashboardTab>, dashboardTabs: List<DashboardTab> ->
      val dashboardTabCapabilities = dashboardTabs.flatMap { it.capabilities }.toSet()

      val h3TextColor = when {
        authenticatedTabs.isEmpty() -> "text-red-800"
        else -> "text-blue-800"
      }
      val hoverTextColor = when {
        authenticatedTabs.isEmpty() -> "hover:text-red-500"
        else -> "hover:text-blue-500"
      }
      val textColor = when {
        authenticatedTabs.isEmpty() -> "text-red-700"
        else -> "text-blue-700"
      }
      val backgroundColor = when {
        authenticatedTabs.isEmpty() -> "bg-red-50"
        else -> "bg-blue-50"
      }
      val iconSvg = when {
        authenticatedTabs.isEmpty() -> {
          """
        <svg class="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clip-rule="evenodd" />
        </svg>
        """.trimIndent()
        }

        else -> {
          """
        <svg class="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z" clip-rule="evenodd" />
        </svg>
        """.trimIndent()
        }
      }

      div("rounded-md $backgroundColor p-4") {
        div("flex") {
          div("flex-shrink-0") {
            unsafe { raw(iconSvg) }
          }
          div("ml-3 flex-1 md:flex md:justify-between") {

            div {
              h3("text-sm font-medium $h3TextColor") { +"""Authenticated Access""" }
              div("mt-2 text-sm $textColor") {
                ul("list-disc space-y-1 pl-5") {
                  role = "list"
                  li { +"""You have access to ${authenticatedTabs.size} / ${dashboardTabs.size} tabs with your capabilities ${caller?.capabilities}.""" }
                  li { +"""Missing access to some dashboard tabs? Ensure you have one of the required capabilities $dashboardTabCapabilities in Access Registry.""" }
                }
              }
            }

            p("mt-3 text-sm md:ml-6 md:mt-0 align-middle float-right") {
              a(classes = "whitespace-nowrap font-medium $textColor $hoverTextColor") {
                href = "#registry/${caller?.principal}"
                +"""Access Registry"""
                span {
                  attributes["aria-hidden"] = "true"
                  +""" →"""
                }
              }
            }
          }
        }
      }
    }
    val dashboardIndexBlock1 = DashboardIndexBlock<AdminDashboard> {
      p { +"""Content 1""" }
    }
    val dashboardIndexBlock2 = DashboardIndexBlock<AdminDashboard> {
      p { +"""Content 2""" }
    }
    install(DashboardModule.addIndexAccessBlocks(dashboardIndexAccessBlock))
    install(DashboardModule.addIndexBlocks(dashboardIndexBlock1, dashboardIndexBlock2))

    // Custom Admin Dashboard Tab at /_admin/...
    install(WebActionModule.createWithPrefix<AlphaIndexAction>("/v2/"))
    install(
      DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
        slug = "exemplar-alpha",
        urlPathPrefix = AlphaIndexAction.PATH,
        menuLabel = "Alpha",
        menuCategory = "Admin Tools"
      )
    )
  }
}

/** Dashboard Annotation used for all tabs bound in the Exemplar service support dashboard. */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class SupportDashboard

/** Access for the support dashboard. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SupportDashboardAccess
