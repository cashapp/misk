package misk.web.metadata

import misk.config.AppName
import misk.config.Config
import misk.inject.KAbstractModule
import misk.web.actions.TestWebActionModule
import misk.web.dashboard.AdminDashboardTestingModule
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardNavbarItem
import misk.web.dashboard.DashboardNavbarStatus
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.DashboardTabProvider
import javax.inject.Qualifier

// Common test module used to be able to test admin dashboard WebActions
class MetadataTestingModule : KAbstractModule() {
  override fun configure() {
    install(TestWebActionModule())
    install(AdminDashboardTestingModule())
    bind<Config>().toInstance(TestAdminDashboardConfig())
    // TODO(wesley): Remove requirement for AppName to bind AdminDashboard APIs
    bind<String>().annotatedWith<AppName>().toInstance("testApp")

    // Bind test dashboard tab, navbar_items, navbar_status
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<DashboardMetadataActionTestDashboard>(
        slug = "slug",
        url_path_prefix = "/url-path-prefix/",
        name = "Test Dashboard Tab",
        category = "test category",
        capabilities = setOf("test_admin_access")
      ))

    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<DashboardMetadataActionTestDashboard>(
        item = "<a href=\"https://cash.app/\">Test Navbar Link</a>",
        order = 1
      )
    )

    multibind<DashboardNavbarStatus>().toInstance(
      DashboardNavbarStatus<DashboardMetadataActionTestDashboard>(
        status = "Test Status")
    )

    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<DashboardMetadataActionTestDashboard>(
        urlPathPrefix = "/test-app/")
    )
  }
}

class TestAdminDashboardConfig : Config

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DashboardMetadataActionTestDashboard
