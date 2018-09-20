package misk.web.metadata

import misk.config.ConfigAdminAction
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.AdminTab
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class ConfigMetadataModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<ConfigAdminAction>())
    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/",
        "Container Admin"
    ))

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