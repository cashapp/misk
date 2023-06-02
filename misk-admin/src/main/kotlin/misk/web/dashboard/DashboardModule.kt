package misk.web.dashboard

import misk.inject.KAbstractModule

/** Handles installation of Misk Dashboard components (admin dashboard or custom...). */
class DashboardModule(
  private val dashboardTabProvider: DashboardTabProvider,
  private val webTabResourceModule: WebTabResourceModule? = null
) : KAbstractModule() {
  override fun configure() {
    multibind<DashboardTab>().toProvider(dashboardTabProvider)
    webTabResourceModule?.let { install(it) }
  }

  companion object {
    /**
     * Create menu link with [label] for [url] under menu [category]
     * for a dashboard [DA] with access [AA].
     *
     * If [category] is empty, it will appear at the top of the menu list.
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createMenuLink(
      label: String,
      url: String,
      category: String = "",
    ): DashboardModule {
      val dashboardTabProvider = DashboardTabProvider(
        slug = "menu-link-$url",
        url_path_prefix = url,
        name = label,
        category = category,
        dashboard_slug = ValidWebEntry.slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      return DashboardModule(
        dashboardTabProvider = dashboardTabProvider,
      )
    }

    /**
     * Installs a Misk-Web tab for a dashboard [DA] with access [AA].
     * The tab is identified by a unique [slug] and is routed to by match on [urlPathPrefix].
     * In local development – when [isDevelopment] is true, the [developmentWebProxyUrl] is used
     * to resolve requests to [resourcePathPrefix]. In real environments,
     * the [classpathResourcePathPrefix] is used to resolve resource requests to files in classpath.
     * The tab is included in the dashboard navbar menu with [name] and in the menu group [category].
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createMiskWebTab(
      isDevelopment: Boolean,
      slug: String,
      urlPathPrefix: String,
      developmentWebProxyUrl: String,
      resourcePathPrefix: String = "/_tab/$slug/",
      classpathResourcePathPrefix: String = "classpath:/web$resourcePathPrefix",
      name: String,
      category: String = "Admin",
    ): DashboardModule {
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
      return DashboardModule(
        dashboardTabProvider = dashboardTabProvider,
        webTabResourceModule = webTabResourceModule
      )
    }
  }
}
