package misk.web.v2

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.DashboardHomeUrl
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import misk.web.v2.DashboardPageLayout.Companion.ADMIN_DASHBOARD_PATH

class NavbarModule : KAbstractModule() {
  override fun configure() {
    multibind<DashboardHomeUrl>().toInstance(DashboardHomeUrl<AdminDashboard>("$ADMIN_DASHBOARD_PATH/"))
    install(WebActionModule.createWithPrefix<DashboardIndexAction>("$ADMIN_DASHBOARD_PATH/"))

    // Favicon.ico and any other shared static assets available at /static/*
    multibind<StaticResourceEntry>()
      .toInstance(StaticResourceEntry(url_path_prefix = "/static/", resourcePath = "classpath:/web/static/"))
    install(WebActionModule.createWithPrefix<StaticResourceAction>(url_path_prefix = "/static/"))
  }
}
