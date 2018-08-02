package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionEntry
import misk.web.actions.AdminTab
import misk.web.actions.WebProxyAction

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<ConfigAdminAction>())

    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/"
    ))
    // TODO(adrw) only add web proxy during development, otherwise add ResourceInterceptor (Jar)
    multibind<WebActionEntry>().toInstance(WebProxyAction.toEntry("/_admin/config/",
        "http://localhost:3200/"))
  }
}