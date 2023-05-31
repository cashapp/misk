package misk.web.dashboard

import misk.inject.KAbstractModule

/** Handles complete installation of a new DashboardTab for use in AdminDashboard or another. */
class DashboardTabModule(
  private val dashboardTabProvider: DashboardTabProvider,
  private val webTabResourceModule: WebTabResourceModule? = null
) : KAbstractModule() {
  override fun configure() {
    multibind<DashboardTab>().toProvider(dashboardTabProvider)
    webTabResourceModule?.let { install(it) }
  }

  companion object {
    /**
     * Installs a Misk-Web tab for a dashboard [DA] with access [AA].
     * The tab is identified by a unique [slug] and is routed to by match on [urlPathPrefix].
     * In local development – when [isDevelopment] is true, the [developmentWebProxyUrl] is used
     * to resolve requests to [resourcePathPrefix]. In real environments,
     * the [classpathResourcePathPrefix] is used to resolve resource requests to files in classpath.
     * The tab is included in the dashboard navbar menu with [name] and in the menu group [category].
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createMiskWeb(
      isDevelopment: Boolean,
      slug: String,
      urlPathPrefix: String,
      developmentWebProxyUrl: String,
      resourcePathPrefix: String = "/_tab/$slug/",
      classpathResourcePathPrefix: String = "classpath:/web$resourcePathPrefix",
      name: String,
      category: String = "Admin",
    ): DashboardTabModule {
      val dashboardTabProvider = DashboardTabProvider(
        slug = slug,
        url_path_prefix = urlPathPrefix,
        name = name,
        category = category,
        dashboard_slug = ValidWebEntry.slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      val webTabResourceModule = WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = slug,
        url_path_prefix = resourcePathPrefix,
        resourcePath = classpathResourcePathPrefix,
        web_proxy_url = developmentWebProxyUrl
      )
      return DashboardTabModule(
        dashboardTabProvider = dashboardTabProvider,
        webTabResourceModule = webTabResourceModule
      )
    }
  }
}
