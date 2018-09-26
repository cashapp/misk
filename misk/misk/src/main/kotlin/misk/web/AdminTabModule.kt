package misk.web

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.AdminTab
import misk.web.actions.AdminTabAction
import misk.web.actions.WebActionEntry
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.metadata.ConfigMetadataModule
import misk.web.metadata.WebActionMetadataModule
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyActionModule
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

/**
 * AdminTabModule
 *
 * Binds the admin UI framework. Individual tabs should be bound with their other code.
 *
 * Example
 * Config tab is tightly coupled to the config module. Thus binding should be in ConfigMetadataModule
 */

class AdminTabModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    // Misc. Necessary Modules and Bindings for AdminTabModule
    install(WebProxyActionModule())
    multibind<WebActionEntry>().toInstance(WebActionEntry<AdminTabAction>())
    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()

    // Tab Modules
    install(ConfigMetadataModule(environment))
    install(WebActionMetadataModule(environment))

    //  AdminTab Bindings
    multibind<AdminTab>().toInstance(AdminTab(
        "Example",
        "example",
        "/_admin/example/",
        "Misk Development"
    ))

    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry("/_admin/", "classpath:/web/_tab/loader"))
    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry("/_tab/example/", "classpath:/web/_tab/example/"))
    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry("/@misk/", "classpath:/web/@misk/"))

    // Environment Dependent WebProxyAction or StaticResourceAction bindings
    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_admin/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_tab/example/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/@misk/"))

      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_admin/", "http://localhost:3100/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_tab/example/", "http://localhost:3199/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/@misk/", "http://localhost:9100/"))

    } else {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_admin/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_tab/example/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/@misk/"))

      // TODO(adrw) needs further testing in production to see if still necessary for serving and bundling Loader tab code
      if (false) {
        multibind<StaticResourceEntry>()
            .toInstance(StaticResourceEntry("/_tab/loader/", "classpath:/web/_tab/loader/"))
      }
    }

    // True for testing Misk Menu with populated tabs and categories, tabs are not functional
    if (true) {
      multibind<AdminTab>().toInstance(AdminTab(
          "URL Lookup",
          "a",
          "/_admin/a/",
          "URL Shortener Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "Domains",
          "a",
          "/_admin/a/",
          "URL Shortener Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "Cookies",
          "a",
          "/_admin/a/",
          "URL Shortener Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "Redirect Config",
          "a",
          "/_admin/a/",
          "URL Shortener Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "URL Eviction",
          "a",
          "/_admin/a/",
          "URL Shortener Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "gRPC client",
          "a",
          "/_admin/a/",
          "Container Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "gRPC server",
          "a",
          "/_admin/a/",
          "Container Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "Threads",
          "a",
          "/_admin/a/",
          "Container Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "Guice",
          "a",
          "/_admin/a/",
          "Container Admin"
      ))
      multibind<AdminTab>().toInstance(AdminTab(
          "Connections",
          "a",
          "/_admin/a/",
          "Container Admin"
      ))
    }
  }
}