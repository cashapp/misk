package misk.web.metadata

import misk.config.ConfigAdminAction
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.DashboardTab
import misk.web.actions.AdminDashboardTab
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class ConfigMetadataModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<ConfigAdminAction>())
    multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
        name = "Config",
        slug = "config",
        url_path_prefix = "/_admin/config/",
        category = "Container Admin"
    ))

    multibind<StaticResourceEntry>()
        .toInstance(StaticResourceEntry(url_path_prefix = "/_tab/config/", resourcePath = "classpath:/web/_tab/config/"))

    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(WebActionEntry<WebProxyAction>(url_path_prefix = "/_tab/config/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = "/_tab/config/", web_proxy_url = "http://localhost:3200/"))
    } else {
      multibind<WebActionEntry>().toInstance(WebActionEntry<StaticResourceAction>(url_path_prefix = "/_tab/config/"))
    }
  }
}