package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionEntry
import misk.web.actions.AdminTab
import misk.web.actions.WebProxyAction
import okhttp3.HttpUrl

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry(ConfigAdminAction::class))
    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/"
    ))
    // TODO(adrw) only add web proxy during development, otherwise add ResourceInterceptor (Jar)
    multibind<WebActionEntry>().toInstance(WebActionEntry(
        WebProxyAction(
        "/_admin/config/",
        HttpUrl.parse("http://localhost:3200/")!!
    ), "/_admin/"))
  }
}