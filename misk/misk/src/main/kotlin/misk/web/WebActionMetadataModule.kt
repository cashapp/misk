package misk.web

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.AdminTab
import misk.web.actions.WebActionEntry
import misk.web.actions.WebActionMetadataAction
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class WebActionMetadataModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<WebActionMetadataAction>())
    multibind<AdminTab>().toInstance(AdminTab(
        name = "Web Actions",
        slug = "webactions",
        url_path_prefix = "/_admin/webactions/"
    ))

    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_tab/webactions/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_tab/webactions/", "http://localhost:3201/"))
    } else {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_tab/webactions/"))
      multibind<StaticResourceEntry>()
          .toInstance(
              StaticResourceEntry("/_tab/webactions/",
                  "classpath:/web/_tab/webactions"))

    }
  }
}