package misk.web.metadata

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.DashboardTab
import misk.web.actions.AdminDashboardTab
import misk.web.actions.WebActionEntry
import misk.web.actions.WebActionMetadataAction
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class WebActionMetadataModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<WebActionMetadataAction>())
    multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
        name = "Web Actions",
        slug = "webactions",
        url_path_prefix = "/_admin/webactions/"
    ))

    multibind<StaticResourceEntry>()
        .toInstance(
            StaticResourceEntry(url_path_prefix = "/_tab/webactions/",
                resourcePath = "classpath:/web/_tab/webactions"))

    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>(url_path_prefix = "/_tab/webactions/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = "/_tab/webactions/", web_proxy_url = "http://localhost:3201/"))
    } else {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>(url_path_prefix = "/_tab/webactions/"))
    }
  }
}