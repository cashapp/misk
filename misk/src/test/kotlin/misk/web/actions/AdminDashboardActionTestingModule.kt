package misk.web.actions

import misk.config.AppName
import misk.config.Config
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.DashboardTab
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

    // Bind test dashboard tab, navbar_items, navbar_status
    multibind<DashboardTab>().toInstance(DashboardTab<DashboardMetadataActionTestDashboard>(
      slug = "slug",
      url_path_prefix = "/url-path-prefix/",
      name = "Test Dashboard Tab",
      category = "test category",
      capabilities = setOf("test_admin_access")
    ))

    multibind<DashboardMetadataAction.DashboardNavbarItem>().toInstance(
      DashboardMetadataAction.DashboardNavbarItem<DashboardMetadataActionTestDashboard>(
        item = "<a href=\"https://cash.app/\">Test Navbar Link</a>",
        order = 1
      )
    )

    multibind<DashboardMetadataAction.DashboardNavbarStatus>().toInstance(
      DashboardMetadataAction.DashboardNavbarStatus<DashboardMetadataActionTestDashboard>(
        status = "Test Status")
    )

    multibind<DashboardMetadataAction.DashboardHomeUrl>().toInstance(
      DashboardMetadataAction.DashboardHomeUrl<DashboardMetadataActionTestDashboard>(
        urlPathPrefix = "/test-app/")
    )
  }
}

class TestAdminDashboardConfig : Config

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DashboardMetadataActionTestDashboard