package misk.hibernate.actions

import misk.MiskCaller
import misk.config.Config
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.AccessControlModule
import misk.security.authz.DevelopmentOnly
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.MiskWebModule
import misk.web.WebTestingModule.Companion.TESTING_WEB_CONFIG
import misk.web.dashboard.AdminDashboardAccess
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess

class HibernateWebActionTestingModule : KAbstractModule() {
  override fun configure() {
    bind<Config>().toInstance(TestConfig())
    install(MiskWebModule(TESTING_WEB_CONFIG))
    install(AccessControlModule())

    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
      .toInstance(MiskCaller(user = "development", capabilities = setOf("users")))
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AdminDashboardAccess>(
        capabilities = listOf(
          "admin_access", "admin_console", "users"
        )
      )
    )
    // Default access that doesn't allow any queries for unconfigured DbEntities
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<NoAdminDashboardDatabaseAccess>(
        capabilities = listOf("no_admin_dashboard_database_access")
      )
    )
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
  }

  class TestConfig : Config
}

