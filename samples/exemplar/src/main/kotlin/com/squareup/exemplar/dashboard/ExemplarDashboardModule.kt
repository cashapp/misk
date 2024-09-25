package com.squareup.exemplar.dashboard

import com.squareup.exemplar.dashboard.admin.AlphaIndexAction
import com.squareup.exemplar.dashboard.admin.dashboardIndexAccessBlock
import com.squareup.exemplar.dashboard.admin.dashboardIndexBlock1
import com.squareup.exemplar.dashboard.admin.dashboardIndexBlock2
import com.squareup.exemplar.dashboard.frontend.EcommerceLandingPage
import com.squareup.exemplar.dashboard.frontend.GraphD3JsPage
import com.squareup.exemplar.dashboard.frontend.IndexPage
import com.squareup.exemplar.dashboard.frontend.SimplePage
import com.squareup.exemplar.dashboard.support.SupportBravoIndexAction
import com.squareup.exemplar.dashboard.support.SupportDashboardIndexAction
import jakarta.inject.Qualifier
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.AdminDashboardModule
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardModule
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import wisp.deployment.Deployment

class ExemplarDashboardModule(private val deployment: Deployment) : KAbstractModule() {
  override fun configure() {
    // Favicon.ico and any other shared static assets available at /static/*
    multibind<StaticResourceEntry>()
      .toInstance(
        StaticResourceEntry(
          url_path_prefix = "/static/",
          resourcePath = "classpath:/web/static/"
        )
      )
    install(WebActionModule.createWithPrefix<StaticResourceAction>(url_path_prefix = "/static/"))

    // Custom Frontend at /app/
    install(WebActionModule.create<EcommerceLandingPage>())
    install(WebActionModule.create<GraphD3JsPage>())
    install(WebActionModule.create<SimplePage>())
    install(WebActionModule.create<IndexPage>())

    // Custom Support Dashboard /support/
    install(WebActionModule.create<SupportDashboardIndexAction>())
    install(WebActionModule.create<SupportBravoIndexAction>())
    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<SupportDashboard>("/support/")
    )
    install(
      DashboardModule.createHotwireTab<SupportDashboard, SupportDashboardAccess>(
        slug = "bravo",
        urlPathPrefix = "/support/bravo/",
        menuLabel = "Bravo",
        menuCategory = "Support"
      )
    )
    install(
      DashboardModule.createMenuLink<SupportDashboard, SupportDashboardAccess>(
        label = "Admin Dashboard",
        url = "/_admin/",
      )
    )
    install(
      DashboardModule.createMenuLink<SupportDashboard, SupportDashboardAccess>(
        label = "Cash App",
        url = "https://cash.app/",
      )
    )

    // Admin Dashboard Setup
    install(
      AdminDashboardModule(
        isDevelopment = true,
        configTabMode = ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS,
      )
    )

    install(DashboardModule.addIndexAccessBlocks(dashboardIndexAccessBlock))
    install(DashboardModule.addIndexBlocks(dashboardIndexBlock1, dashboardIndexBlock2))

    // Custom Admin Dashboard Tab at /_admin/...
    install(WebActionModule.create<AlphaIndexAction>())
    install(
      DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
        slug = "exemplar-alpha",
        urlPathPrefix = AlphaIndexAction.PATH,
        menuLabel = "Alpha",
        menuCategory = "Admin Tools"
      )
    )

    // Custom links
    install(DashboardModule.createMenuLink<AdminDashboard, AdminDashboardAccess>(
      label = { appName, deployment -> "Internal Tool" },
      url = { appName, deployment -> "https://internal-tool.cash.app/?app=$appName&deployment=$deployment" },
      category = "Internal"
    ))

    // Custom Admin Dashboard Tab at /_admin/... which doesn't exist and shows graceful failure 404
    install(WebActionModule.create<AlphaIndexAction>())
    install(
      DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
        isDevelopment = deployment.isLocalDevelopment,
        slug = "not-found",
        urlPathPrefix = "/_admin/not-found/",
        developmentWebProxyUrl = "http://localhost:3000/",
        menuLabel = "Not Found",
        menuCategory = "Admin Tools"
      )
    )
  }
}

/** Dashboard Annotation used for all tabs bound in the Exemplar service support dashboard. */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class SupportDashboard

/** Access for the support dashboard. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SupportDashboardAccess
