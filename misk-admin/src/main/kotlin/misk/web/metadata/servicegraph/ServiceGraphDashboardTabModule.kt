package misk.web.metadata.servicegraph

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule

class ServiceGraphDashboardTabModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ServiceGraphTabIndexAction>())
    install(
      DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
        slug = "service-graph",
        urlPathPrefix = ServiceGraphTabIndexAction.PATH,
        menuCategory = "Container Admin",
        menuLabel = "Service Graph",
      )
    )
  }
}
