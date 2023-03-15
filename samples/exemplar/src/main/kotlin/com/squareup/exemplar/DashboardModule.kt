package com.squareup.exemplar

import misk.inject.KAbstractModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardModule
import misk.web.dashboard.DashboardTheme
import misk.web.dashboard.EnvironmentToColorLookup
import misk.web.dashboard.MiskWebColor
import misk.web.dashboard.MiskWebTheme
import misk.web.metadata.config.ConfigMetadataAction

class DashboardModule : KAbstractModule() {
  override fun configure() {
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
