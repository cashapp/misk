package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.DashboardMetadataAction
import misk.web.metadata.ServiceMetadataAction

/** Support Misk-Web Dashboards including the Misk [AdminDashboard] and service specific front end apps */
class DashboardModule : KAbstractModule() {
  override fun configure() {
    // Setup multibindings for dashboard related components
    newMultibinder<DashboardTab>()
    newMultibinder<DashboardHomeUrl>()
    newMultibinder<DashboardNavbarItem>()
    newMultibinder<DashboardNavbarStatus>()
    newMultibinder<DashboardTheme>()

    // Add metadata actions to support dashboards
    install(WebActionModule.create<DashboardMetadataAction>())
    install(WebActionModule.create<ServiceMetadataAction>())
  }
}
