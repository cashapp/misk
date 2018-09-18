package misk.web

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.AdminTab
import misk.web.actions.AdminTabAction
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

/**
 * AdminTabModule
 *
 * Binds the admin UI framework. Individual tabs should be bound with their other code.
 *
 * Example
 * Config tab is tightly coupled to the config module. Thus binding should be in ConfigWebModule
 */

class AdminTabModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<AdminTabAction>())

    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_admin/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_admin/@misk/"))

      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_admin/", "http://localhost:3100/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_admin/@misk/", "http://localhost:9100/"))

    } else {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_admin/"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_admin/@misk/"))

      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/_admin/", "classpath:/web/_admin/"))
      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/_admin/@misk/", "classpath:/web/_admin/@misk/"))

      // TODO(adrw) needs further testing in production to see if still necessary for serving and bundling Loader tab code
      if (false) {
        multibind<StaticResourceEntry>()
            .toInstance(StaticResourceEntry("/_tab/loader/", "classpath:/web/_tab/loader/"))
      }
    }

    // All simple Admin Tabs + Endpoints
    install(WebActionMetadataModule(environment))

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