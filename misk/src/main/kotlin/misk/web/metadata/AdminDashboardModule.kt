package misk.web.metadata

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import misk.web.actions.AdminDashboardTab
import misk.web.actions.AdminDashboardTabAction
import misk.web.actions.ServiceMetadata
import misk.web.actions.ServiceMetadataAction
import misk.web.actions.WebActionEntry
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyActionModule
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

/**
 * Installs default Admin Dashboard that runs at passed in url_path_prefix
 * Each Misk included tab in dashboard is installed with the respective tab module (ie. ConfigMetadataModule)
 * Non-Misk tabs can be added by binding AdminDashboardTab entries which will be displayed in the dashboard menu
 */
class AdminDashboardModule(
  val environment: Environment,
  val name: String,
  val url_path_prefix: String
) : KAbstractModule() {
  override fun configure() {
    // Misc. Necessary Modules and Bindings for AdminDashboardModule
    multibind<ServiceMetadata>().toInstance(ServiceMetadata(
        name = name,
        url = url_path_prefix,
        environment = environment
    ))
    install(WebProxyActionModule())
    multibind<WebActionEntry>().toInstance(WebActionEntry<AdminDashboardTabAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<ServiceMetadataAction>())
    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()

    // Tab Modules
    install(ConfigMetadataModule(environment))
    install(WebActionMetadataModule(environment))

    //  AdminDashboardTab Bindings
    multibind<AdminDashboardTab>().toInstance(AdminDashboardTab(
        name = "Example",
        slug = "example",
        url_path_prefix = "${url_path_prefix}example/",
        category = "Misk Development"
    ))

    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry(url_path_prefix = url_path_prefix,
            resourcePath = "classpath:/web/_tab/loader/"))
    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry(url_path_prefix = "/_tab/loader/",
            resourcePath = "classpath:/web/_tab/loader/"))
    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry(url_path_prefix = "/_tab/example/",
            resourcePath = "classpath:/web/_tab/example/"))
    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry(url_path_prefix = "/@misk/",
            resourcePath = "classpath:/web/@misk/"))

    // Environment Dependent WebProxyAction or StaticResourceAction bindings
    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>(url_path_prefix = url_path_prefix))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>(url_path_prefix = "/_tab/loader/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>(url_path_prefix = "/_tab/example/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>(url_path_prefix = "/@misk/"))

      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = url_path_prefix,
              web_proxy_url = "http://localhost:3100/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = "/_tab/loader/",
              web_proxy_url = "http://localhost:3100/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = "/_tab/example/",
              web_proxy_url = "http://localhost:3199/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = "/@misk/", web_proxy_url = "http://localhost:9100/"))

    } else {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>(url_path_prefix = url_path_prefix))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>(url_path_prefix = "/_tab/loader/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>(url_path_prefix = "/_tab/example/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>(url_path_prefix = "/@misk/"))
    }

    // True for testing Misk Menu with populated tabs and categories, tabs are not functional
    if (true) {
      multibind<AdminDashboardTab>().toInstance(
          AdminDashboardTab(
              name = "gRPC client",
              slug = "a",
              url_path_prefix = "${url_path_prefix}a/",
              category = "Container Admin"
          ))
      multibind<AdminDashboardTab>().toInstance(
          AdminDashboardTab(
              name = "gRPC server",
              slug = "a",
              url_path_prefix = "${url_path_prefix}a/",
              category = "Container Admin"
          ))
      multibind<AdminDashboardTab>().toInstance(
          AdminDashboardTab(
              name = "Threads",
              slug = "a",
              url_path_prefix = "${url_path_prefix}a/",
              category = "Container Admin"
          ))
      multibind<AdminDashboardTab>().toInstance(
          AdminDashboardTab(
              name = "Guice",
              slug = "a",
              url_path_prefix = "${url_path_prefix}a/",
              category = "Container Admin"
          ))
      multibind<AdminDashboardTab>().toInstance(
          AdminDashboardTab(
              name = "Connections",
              slug = "a",
              url_path_prefix = "${url_path_prefix}a/",
              category = "Container Admin"
          ))
    }
  }
}