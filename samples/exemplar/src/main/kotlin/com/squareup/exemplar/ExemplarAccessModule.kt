package com.squareup.exemplar

import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.security.authz.*
import misk.web.dashboard.AdminDashboardAccess

class ExemplarAccessModule : KAbstractModule() {
  override fun configure() {
    install(AccessControlModule())
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()

    // Give engineers access to the admin dashboard for Exemplar service
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AdminDashboardAccess>(
      capabilities = listOf("admin_console"))
    )

    // Setup authentication in the development environment
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
      .toInstance(MiskCaller(user = "development", capabilities = setOf("admin_console", "users")))
  }
}
