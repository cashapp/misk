package misk.web.v2

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardNavbarItem
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import misk.web.v2.DashboardPageLayout.Companion.ADMIN_DASHBOARD_PATH
import misk.web.v2.DashboardPageLayout.Companion.BETA_PREFIX

class BaseDashboardV2Module : KAbstractModule() {
  override fun configure() {
    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<AdminDashboard>("$BETA_PREFIX$ADMIN_DASHBOARD_PATH/")
    )

    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/\">Admin Dashboard v1</a>",
        order = 500
      )
    )
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/v2/_admin/\">Admin Dashboard v2</a>",
        order = 501
      )
    )

    install(WebActionModule.createWithPrefix<DashboardIndexAction>("$BETA_PREFIX$ADMIN_DASHBOARD_PATH/"))

    // Favicon.ico and any other shared static assets available at /static/*
    multibind<StaticResourceEntry>()
      .toInstance(
        StaticResourceEntry(
          url_path_prefix = "/static/",
          resourcePath = "classpath:/web/static/"
        )
      )
    install(WebActionModule.createWithPrefix<StaticResourceAction>(url_path_prefix = "/static/"))
  }
}
