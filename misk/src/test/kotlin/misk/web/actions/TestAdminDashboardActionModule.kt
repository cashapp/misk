package misk.web.actions

import misk.config.AppName
import misk.config.Config
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.metadata.AdminDashboardTestingModule

// Common test module used to be able to test admin dashboard WebActions
class TestAdminDashboardActionModule : KAbstractModule() {
  override fun configure() {
    install(TestWebActionModule())
    install(AdminDashboardTestingModule(Environment.TESTING))
    bind<Config>().toInstance(TestAdminDashboardConfig())
    // TODO(wesley): Remove requirement for AppName to bind AdminDashboard APIs
    bind<String>().annotatedWith<AppName>().toInstance("testApp")
  }
}

class TestAdminDashboardConfig : Config
