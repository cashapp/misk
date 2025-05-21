package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.ValidWebEntry.Companion.slugify
import misk.web.v2.DashboardHotwireTabAction
import misk.web.v2.DashboardIFrameTabAction
import misk.web.v2.DashboardIndexAccessBlock
import misk.web.v2.DashboardIndexBlock
import okio.ByteString.Companion.encodeUtf8
import wisp.deployment.Deployment
import wisp.deployment.PRODUCTION

/** Handles installation of Misk Dashboard components (admin dashboard or custom...). */
class DashboardModule @JvmOverloads constructor(
  private val dashboardTabProvider: DashboardTabProvider? = null,
  private val dashboardTabLoader: DashboardTabLoader? = null,
  private val webTabResourceModule: WebTabResourceModule? = null,
  private val indexAccessBlocks: List<DashboardIndexAccessBlock> = listOf(),
  private val indexBlocks: List<DashboardIndexBlock> = listOf(),
) : KAbstractModule() {
  override fun configure() {
    dashboardTabProvider?.let { multibind<DashboardTab>().toProvider(it) }
    dashboardTabLoader?.let {
      multibind<DashboardTabLoaderEntry>().toInstance(
        DashboardTabLoaderEntry(it.urlPathPrefix, it)
      )
      multibind<DashboardTabLoader>().toInstance(it)

      when (it) {
        is DashboardTabLoader.HotwireTab -> {
          install(WebActionModule.createWithPrefix<DashboardHotwireTabAction>(it.urlPathPrefix))
        }
        is DashboardTabLoader.IframeTab -> {
          install(WebActionModule.createWithPrefix<DashboardIFrameTabAction>(it.urlPathPrefix))
        }
      }
    }

    webTabResourceModule?.let { install(it) }

    indexAccessBlocks.forEach {
      multibind<DashboardIndexAccessBlock>().toInstance(it)
    }

    indexBlocks.forEach {
      multibind<DashboardIndexBlock>().toInstance(it)
    }
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
      // Create a unique hash for the link that will not conflict with any other link
      val hash = (label + url + category).encodeUtf8().sha256().hex().take(24)
      val dashboardTabProvider = DashboardTabProvider(
        slug = "menu-link-$hash",
        url_path_prefix = url,
        menuLabel = { _, _ -> label },
        menuUrl = { _, _ -> url },
        menuCategory = category,
        dashboard_slug = slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      return DashboardModule(
        dashboardTabProvider = dashboardTabProvider,
      )
    }

    /**
     * Create menu link with [label] for [url] under menu [category]
     *   for a dashboard [DA] with access [AA].
     *
     * If [category] is empty, it will appear at the top of the menu list.
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createMenuLink(
      noinline label: (appName: String, deployment: Deployment) -> String,
      noinline url: (appName: String, deployment: Deployment) -> String,
      category: String = "",
    ): DashboardModule {
      // Create a unique hash for the link that will not conflict with any other link
      val hash = (label.toString() + url.toString() + category).encodeUtf8().sha256().hex().take(24)
      val dashboardTabProvider = DashboardTabProvider(
        slug = "menu-link-$hash",
        url_path_prefix = url("app", PRODUCTION),
        menuLabel = label,
        menuUrl = url,
        menuCategory = category,
        dashboard_slug = slugify<DA>(),
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
        menuLabel = { _, _ -> menuLabel },
        menuUrl = { _, _ -> menuUrl },
        menuCategory = menuCategory,
        dashboard_slug = slugify<DA>(),
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
     * @param menuLabel: tab name which appears in the dashboard menu, usually titlecase
     * @param menuCategory: menu category which the tab appears under
     * @param menuUrl: menu link url if different from [urlPathPrefix]
     * @param isDevelopment: true if this deployment is in development environment
     * @param developmentWebProxyUrl: url to a local resource server which will be used to resolve requests to
     *  [resourcePathPrefix] in development environments.
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createIFrameTab(
      slug: String,
      urlPathPrefix: String,
      resourcePathPrefix: String = "/_tab/$slug/",
      iframePath: String,
      menuLabel: String,
      menuUrl: String = urlPathPrefix,
      menuCategory: String = "Admin",
      isDevelopment: Boolean = false,
      developmentWebProxyUrl: String? = null,
    ): DashboardModule {
      val dashboardTabLoader = DashboardTabLoader.IframeTab(
        urlPathPrefix = urlPathPrefix,
        iframePath = iframePath
      )
      val dashboardTabProvider = DashboardTabProvider(
        slug = slug,
        url_path_prefix = urlPathPrefix,
        menuLabel = { _, _ -> menuLabel },
        menuUrl = { _, _ -> menuUrl },
        menuCategory = menuCategory,
        dashboard_slug = slugify<DA>(),
        accessAnnotationKClass = AA::class,
        dashboardAnnotationKClass = DA::class,
      )
      val webTabResourceModule = WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = slug,
        url_path_prefix = resourcePathPrefix,
        web_proxy_url = developmentWebProxyUrl
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
        menuLabel = { _, _ -> menuLabel },
        menuUrl = { _, _ -> menuUrl },
        menuCategory = menuCategory,
        dashboard_slug = slugify<DA>(),
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

    /**
     * Installs a Misk-Web app for a dashboard [DA] with access [AA].
     * The tab is identified by a unique [slug] and is routed to by match on [urlPathPrefix].
     * In local development – when [isDevelopment] is true, the [developmentWebProxyUrl] is used
     * to resolve requests to [resourcePathPrefix]. In real environments,
     * the [classpathResourcePathPrefix] is used to resolve resource requests to files in classpath.
     * The tab is included in the dashboard navbar menu with [menuLabel] and in the menu group [menuCategory].
     */
    inline fun <reified DA : Annotation, reified AA : Annotation> createMiskWebDashboard(
      isDevelopment: Boolean,
      slug: String,
      urlPathPrefix: String,
      developmentWebProxyUrl: String,
      resourcePathPrefix: String = "/_tab/$slug/",
      classpathResourcePathPrefix: String = "classpath:/web$resourcePathPrefix",
      menuLabel: String,
      menuUrl: String = urlPathPrefix,
      menuCategory: String = "Admin",
    ): DashboardModule {
      val dashboardTabProvider = DashboardTabProvider(
        slug = slug,
        url_path_prefix = urlPathPrefix,
        menuLabel = { _, _ -> menuLabel },
        menuUrl = { _, _ -> menuUrl },
        menuCategory = menuCategory,
        dashboard_slug = slugify<DA>(),
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
        dashboardTabLoader = null,
        webTabResourceModule = webTabResourceModule
      )
    }

    /**
     * Add access blocks to dashboard index.
     */
    fun addIndexAccessBlocks(
      vararg blocks: DashboardIndexAccessBlock
    ) = DashboardModule(indexAccessBlocks = blocks.toList())

    /**
     * Add access blocks to dashboard index.
     */
    fun addIndexBlocks(
      vararg blocks: DashboardIndexBlock
    ) = DashboardModule(indexBlocks = blocks.toList())
  }
}
