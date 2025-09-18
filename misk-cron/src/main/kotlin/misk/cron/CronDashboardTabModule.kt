package misk.cron

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule
import misk.web.metadata.MetadataModule

class CronDashboardTabModule: KAbstractModule() {
  override fun configure() {
    install(MetadataModule(CronMetadataProvider()))
    install(WebActionModule.create<CronTabIndexAction>())
    install(WebActionModule.create<CronTabRunAction>())
    install(DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
      slug = "cron",
      urlPathPrefix = CronTabIndexAction.PATH,
      menuCategory = "Container Admin",
      menuLabel = "Cron",
    ))
  }
}
