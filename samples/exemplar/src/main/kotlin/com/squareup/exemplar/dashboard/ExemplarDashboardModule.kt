package com.squareup.exemplar.dashboard

import com.squareup.exemplar.dashboard.frontend.FrontendIndexAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardModule
import misk.web.dashboard.DashboardTheme
import misk.web.dashboard.EnvironmentToColorLookup
import misk.web.dashboard.MiskWebColor
import misk.web.dashboard.MiskWebTheme
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

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
    install(WebActionModule.create<FrontendIndexAction>())

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
  }
}
