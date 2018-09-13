package misk.config

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.actions.AdminTab
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class ConfigWebModule(val environment: Environment): KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<ConfigAdminAction>())

    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/",
        "Container Admin"
    ))



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



    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(WebActionEntry<WebProxyAction>("/_tab/config/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_tab/config/", "http://localhost:3200/"))
    } else {
      multibind<WebActionEntry>().toInstance(WebActionEntry<StaticResourceAction>("/_tab/config/"))
      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/_tab/config/", "classpath:/web/_tab/config/"))
    }
  }
}