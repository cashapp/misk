package misk.config

import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.actions.AdminTab
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<ConfigAdminAction>())

    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/"
    ))
    // TODO(adrw) only add web proxy during development, otherwise add ResourceInterceptor (Jar)
    multibind<WebProxyEntry>().toInstance(
        WebProxyEntry("/_admin/config", "http://localhost:3200/"))
    multibind<WebActionEntry>().toInstance(WebActionEntry<WebProxyAction>("/_admin/config"))
  }
}