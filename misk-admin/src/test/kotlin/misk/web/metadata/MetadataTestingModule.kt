package misk.web.metadata

import jakarta.inject.Qualifier
import misk.config.AppName
import misk.config.MiskConfig
import misk.config.Redact
import misk.config.Secret
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.actions.TestWebActionModule
import misk.web.dashboard.AdminDashboardTestingModule
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardNavbarItem
import misk.web.dashboard.DashboardNavbarStatus
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.DashboardTabProvider
import misk.web.dashboard.DashboardTheme
import misk.web.dashboard.MiskWebTheme
import misk.web.metadata.all.AllMetadataAccess
import misk.web.metadata.all.AllMetadataModule
import wisp.config.Config

// Common test module used to be able to test admin dashboard WebActions
class MetadataTestingModule : KAbstractModule() {
  override fun configure() {
    install(TestWebActionModule())
    install(AdminDashboardTestingModule())

    install(AllMetadataModule())
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AllMetadataAccess>()
    )

    val testConfig = TestConfig(
      IncludedConfig("foo"),
      OverriddenConfig("bar"),
      PasswordConfig("pass1", "phrase2", "custom3"),
      SecretConfig(MiskConfig.RealSecret("value", "reference")),
      RedactedConfig("baz")
    )
    bind<Config>().toInstance(testConfig)
    // TODO(wesley): Remove requirement for AppName to bind AdminDashboard APIs
    bind<String>().annotatedWith<AppName>().toInstance("admin-dashboard-app")

    // Bind test dashboard tab, navbar_items, navbar_status
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<DashboardMetadataActionTestDashboard>(
        slug = "slug",
        url_path_prefix = "/url-path-prefix/",
        name = "Test Dashboard Tab",
        category = "test category",
        capabilities = setOf("test_admin_access")
      )
    )

    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<DashboardMetadataActionTestDashboard>(
        item = "<a href=\"https://cash.app/\">Test Navbar Link</a>",
        order = 1
      )
    )

    multibind<DashboardNavbarStatus>().toInstance(
      DashboardNavbarStatus<DashboardMetadataActionTestDashboard>(
        status = "Test Status"
      )
    )

    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<DashboardMetadataActionTestDashboard>(
        urlPathPrefix = "/test-app/"
      )
    )

    multibind<DashboardTheme>().toInstance(
      DashboardTheme<DashboardMetadataActionTestDashboard>(
        theme = MiskWebTheme.DEFAULT_THEME
      )
    )
  }
}

data class TestConfig(
  val included: IncludedConfig,
  val overridden: OverriddenConfig,
  val password: PasswordConfig,
  val secret: SecretConfig,
  val redacted: RedactedConfig
) : Config

data class IncludedConfig(val key: String) : Config
data class OverriddenConfig(val key: String) : Config

data class PasswordConfig(
  @Redact
  val password: String,
  @Redact
  val passphrase: String,
  @Redact
  val custom: String
) : Config

@Redact
data class RedactedConfig(
  val key: String
)

data class SecretConfig(val secret_key: Secret<String>) : Config

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DashboardMetadataActionTestDashboard
