package misk.web.actions

import misk.config.AppName
import misk.config.Config
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.DashboardTab
import misk.web.DashboardTabProvider
import misk.web.metadata.AdminDashboardTestingModule
import javax.inject.Qualifier

// Common test module used to be able to test admin dashboard WebActions
class AdminDashboardActionTestingModule : KAbstractModule() {
  override fun configure() {
    install(TestWebActionModule())
    install(AdminDashboardTestingModule(Environment.TESTING))
    bind<Config>().toInstance(TestAdminDashboardConfig())
    // TODO(wesley): Remove requirement for AppName to bind AdminDashboard APIs
    bind<String>().annotatedWith<AppName>().toInstance("testApp")

    // Bind test dashboard tab
    multibind<DashboardTab>().toProvider(DashboardTabProvider<DashboardMetadataActionTestDashboard>(
      slug = "slug",
      url_path_prefix = "/url-path-prefix/",
      name = "Test Dashboard Tab",
      category = "test category",
      capabilities = setOf("test_admin_access")
    ))
  }
}

class TestAdminDashboardConfig : Config

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DashboardMetadataActionTestDashboard