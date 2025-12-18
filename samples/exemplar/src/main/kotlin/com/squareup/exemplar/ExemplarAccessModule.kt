package com.squareup.exemplar

import com.squareup.exemplar.dashboard.SupportDashboardAccess
import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.security.authz.*
import misk.web.dashboard.AdminDashboardAccess

class ExemplarAccessModule : KAbstractModule() {
  override fun configure() {
    install(AccessControlModule())
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()

    // Give engineers access to the admin dashboard for Exemplar service
    multibind<AccessAnnotationEntry>()
      .toInstance(AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("admin_console")))

    // Give engineers access to the admin dashboard for Exemplar service
    multibind<AccessAnnotationEntry>()
      .toInstance(AccessAnnotationEntry<SupportDashboardAccess>(capabilities = listOf("customer_support")))

    // Setup authentication in the development environment
    bind<MiskCaller>()
      .annotatedWith<DevelopmentOnly>()
      .toInstance(MiskCaller(user = "triceratops", capabilities = setOf("admin_console", "customer_support", "users")))
  }
}
