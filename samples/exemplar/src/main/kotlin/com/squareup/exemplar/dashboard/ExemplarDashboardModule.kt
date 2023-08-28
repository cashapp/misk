package com.squareup.exemplar.dashboard

import com.squareup.exemplar.dashboard.admin.AlphaIndexAction
import com.squareup.exemplar.dashboard.frontend.EcommerceLandingPage
import com.squareup.exemplar.dashboard.frontend.IndexPage
import com.squareup.exemplar.dashboard.frontend.SimplePage
import com.squareup.exemplar.dashboard.support.SupportBravoIndexAction
import com.squareup.exemplar.dashboard.support.SupportDashboardIndexAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.AdminDashboardModule
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardModule
import misk.web.dashboard.DashboardTheme
import misk.web.dashboard.EnvironmentToColorLookup
import misk.web.dashboard.MiskWebColor
import misk.web.dashboard.MiskWebTheme
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import jakarta.inject.Qualifier
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.unsafe
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
    install(WebActionModule.create<SimplePage>())
    install(WebActionModule.create<IndexPage>())

    // Custom Support Dashboard /support/
    install(WebActionModule.create<SupportDashboardIndexAction>())
    install(WebActionModule.create<SupportBravoIndexAction>())
    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<SupportDashboard>("/support/")
    )
    install(DashboardModule.createHotwireTab<SupportDashboard, SupportDashboardAccess>(
      slug = "bravo",
      urlPathPrefix = "/support/bravo/",
      menuLabel = "Bravo",
      menuCategory = "Support"
    ))
    install(DashboardModule.createMenuLink<SupportDashboard, SupportDashboardAccess>(
      label = "Admin Dashboard",
      url = "/_admin/",
    ))
    install(DashboardModule.createMenuLink<SupportDashboard, SupportDashboardAccess>(
      label = "Cash App",
      url = "https://cash.app/",
    ))

    // Admin Dashboard Setup
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
    install(
      AdminDashboardModule(
        isDevelopment = true,
        configTabMode = ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG
      )
    )
    multibind<DashboardIndexBlock>().toInstance(DashboardIndexBlock<AdminDashboard> {
      div("rounded-md bg-blue-50 p-4") {
        div("flex") {
          div("flex-shrink-0") {
            unsafe {
              raw("""
                <svg class="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z" clip-rule="evenodd" />
                </svg>
              """.trimIndent())
            }
          }
          div("ml-3 flex-1 md:flex md:justify-between") {
            p("text-sm text-blue-700") { +"""Missing access to some dashboard tabs? Ensure you have the admin_console capability in Access Registry.""" }
            p("mt-3 text-sm md:ml-6 md:mt-0") {
              a(classes = "whitespace-nowrap font-medium text-blue-700 hover:text-blue-600") {
                href = "#"
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
    })

    // Custom Admin Dashboard Tab at /_admin/...
    install(WebActionModule.createWithPrefix<AlphaIndexAction>("/v2/"))
    install(DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
      slug = "exemplar-alpha",
      urlPathPrefix = AlphaIndexAction.PATH,
      menuLabel = "Alpha",
      menuCategory = "Admin Tools"
    ))
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
