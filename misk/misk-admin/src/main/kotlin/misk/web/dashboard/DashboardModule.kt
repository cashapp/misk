package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.v2.DashboardHotwireTabAction
import misk.web.v2.DashboardIFrameTabAction
import misk.web.v2.DashboardPageLayout.Companion.BETA_PREFIX

/** Handles installation of Misk Dashboard components (admin dashboard or custom...). */
class DashboardModule(
  private val dashboardTabProvider: DashboardTabProvider,
  private val dashboardTabLoader: DashboardTabLoader? = null,
  private val webTabResourceModule: WebTabResourceModule? = null
) : KAbstractModule() {
  override fun configure() {
    multibind<DashboardTab>().toProvider(dashboardTabProvider)
    dashboardTabLoader?.let {
      multibind<DashboardTabLoaderEntry>().toInstance(
        DashboardTabLoaderEntry(
          "$BETA_PREFIX${dashboardTabLoader.urlPathPrefix}",
          dashboardTabLoader
        )
      )
      multibind<DashboardTabLoader>().toInstance(dashboardTabLoader)
    }

    when (dashboardTabLoader) {
      is DashboardTabLoader.HotwireTab -> {
        install(WebActionModule.createWithPrefix<DashboardHotwireTabAction>("$BETA_PREFIX${dashboardTabLoader.urlPathPrefix}"))
      }

      is DashboardTabLoader.IframeTab -> {
        install(WebActionModule.createWithPrefix<DashboardIFrameTabAction>(url_path_prefix = "$BETA_PREFIX${dashboardTabLoader.urlPathPrefix}"))
      }

      else -> {}
    }
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
        menuLabel = label,
        menuUrl = url,
        menuCategory = category,
        dashboard_slug = ValidWebEntry.slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      return DashboardModule(
        dashboardTabProvider = dashboardTabProvider,
      )
    }

    /**
     * @param slug: unique slug to identify the tab namespace, it must match the tab's corresponding
     *   [DashboardTab] multibinding
     * @param urlPathPrefix: path prefix which when used in a user's navigation URL will route to this tab.
     * @param menuLabel: tab name which appears in the dashboard menu, usually titlecase
     * @param menuCategory: menu category which the tab appears under
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createHotwireTab(
      slug: String,
      urlPathPrefix: String,
      menuLabel: String,
      menuUrl: String = urlPathPrefix,
      menuCategory: String = "Admin",
    ): DashboardModule {
      val dashboardTabLoader = DashboardTabLoader.HotwireTab(
        urlPathPrefix = urlPathPrefix,
      )
      val dashboardTabProvider = DashboardTabProvider(
        slug = slug,
        url_path_prefix = urlPathPrefix,
        menuLabel = menuLabel,
        menuUrl = menuUrl,
        menuCategory = menuCategory,
        dashboard_slug = ValidWebEntry.slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      return DashboardModule(
        dashboardTabProvider = dashboardTabProvider,
        dashboardTabLoader = dashboardTabLoader,
      )
    }

    /**
     * @param slug: unique slug to identify the tab namespace, it must match the tab's corresponding
     *   [DashboardTab] multibinding
     * @param urlPathPrefix: path prefix which when used in a user's navigation URL will route to this tab.
     * @param resourcePathPrefix: path prefix used for background network requests to get resources
     *   (HTML, CSS...) for the tab from a resource provider (classpath, filesystem, web proxy...).
     * @param iframePath: complete path including file and extension if necessary which is set as the
     *   iframe src attribute in the generated HTML.
     * @param name: tab name which appears in the dashboard menu, usually titlecase
     * @param category: menu category which the tab appears under
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createIFrameTab(
      slug: String,
      urlPathPrefix: String,
      resourcePathPrefix: String = "/_tab/$slug/",
      iframePath: String,
      menuLabel: String,
      menuUrl: String = urlPathPrefix,
      menuCategory: String = "Admin",
    ): DashboardModule {
      val dashboardTabLoader = DashboardTabLoader.IframeTab(
        urlPathPrefix = urlPathPrefix,
        iframePath = iframePath
      )
      val dashboardTabProvider = DashboardTabProvider(
        slug = slug,
        url_path_prefix = urlPathPrefix,
        menuLabel = menuLabel,
        menuUrl = menuUrl,
        menuCategory = menuCategory,
        dashboard_slug = ValidWebEntry.slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      val webTabResourceModule = WebTabResourceModule(
        slug = slug,
        url_path_prefix = resourcePathPrefix,
      )
      return DashboardModule(
        dashboardTabProvider = dashboardTabProvider,
        dashboardTabLoader = dashboardTabLoader,
        webTabResourceModule = webTabResourceModule
      )
    }

    /**
     * Installs a Misk-Web tab for a dashboard [DA] with access [AA].
     * The tab is identified by a unique [slug] and is routed to by match on [urlPathPrefix].
     * In local development – when [isDevelopment] is true, the [developmentWebProxyUrl] is used
     * to resolve requests to [resourcePathPrefix]. In real environments,
     * the [classpathResourcePathPrefix] is used to resolve resource requests to files in classpath.
     * The tab is included in the dashboard navbar menu with [menuLabel] and in the menu group [menuCategory].
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createMiskWebTab(
      isDevelopment: Boolean,
      slug: String,
      urlPathPrefix: String,
      developmentWebProxyUrl: String,
      resourcePathPrefix: String = "/_tab/$slug/",
      classpathResourcePathPrefix: String = "classpath:/web$resourcePathPrefix",
      iframePath: String = "${MiskWebTabIndexAction.PATH}/${slug}/",
      menuLabel: String,
      menuUrl: String = urlPathPrefix,
      menuCategory: String = "Admin",
    ): DashboardModule {
      val dashboardTabLoader = DashboardTabLoader.IframeTab(
        urlPathPrefix = urlPathPrefix,
        iframePath = iframePath
      )
      val dashboardTabProvider = DashboardTabProvider(
        slug = slug,
        url_path_prefix = urlPathPrefix,
        menuLabel = menuLabel,
        menuUrl = menuUrl,
        menuCategory = menuCategory,
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
        dashboardTabLoader = dashboardTabLoader,
        webTabResourceModule = webTabResourceModule
      )
    }
  }
}
