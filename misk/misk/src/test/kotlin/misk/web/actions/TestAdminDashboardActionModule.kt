package misk.web.actions

import misk.config.AppName
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.metadata.AdminDashboardAccess
import misk.web.metadata.AdminDashboardModule

// Common test module used to be able to test admin dashboard WebActions
class TestAdminDashboardActionModule : KAbstractModule() {
  override fun configure() {
    install(TestWebActionModule())
    install(AdminDashboardModule(Environment.TESTING))
    multibind<AccessAnnotationEntry>()
        .toInstance(AccessAnnotationEntry<AdminDashboardAccess>(roles = listOf("engineers")))
    // TODO(wesley): Remove requirement for AppName to bind AdminDashboard APIs
    bind<String>().annotatedWith<AppName>().toInstance("testApp")
  }
}