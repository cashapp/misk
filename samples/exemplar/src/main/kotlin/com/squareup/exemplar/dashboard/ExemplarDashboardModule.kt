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
