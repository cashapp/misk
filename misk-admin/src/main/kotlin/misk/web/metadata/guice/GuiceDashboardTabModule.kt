package misk.web.metadata.guice

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule

class GuiceDashboardTabModule: KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<GuiceTabIndexAction>())
    install(DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
      slug = "guice",
      urlPathPrefix = GuiceTabIndexAction.PATH,
      menuCategory = "Container Admin",
      menuLabel = "Guice",
    ))
  }
}
