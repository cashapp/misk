package misk.config

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.actions.AdminTab
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceMapper

class ConfigWebModule(val environment: Environment): KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<ConfigAdminAction>())

    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config",
        "cog"
    ))
    multibind<WebActionEntry>().toInstance(WebActionEntry<WebProxyAction>("/_tab/config"))

    // TODO(adrw) only add web proxy during development, otherwise add ResourceInterceptor (Jar)
    if (environment == Environment.DEVELOPMENT) {
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_tab/config", "http://localhost:3200/"))
    } else {
      multibind<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/_tab/config/", "classpath:/web/_tab/config/"))
    }
  }
}