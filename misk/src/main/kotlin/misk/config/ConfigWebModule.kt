package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.actions.AdminTab
import misk.web.resources.WebProxyInterceptor
import okhttp3.HttpUrl

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigAdminAction>())
    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/"
    ))
    // TODO(adrw) only add web proxy during development, otherwise add ResourceInterceptor (Jar)
    multibind<WebProxyInterceptor.Mapping>().toInstance(
        WebProxyInterceptor.Mapping(
        "/_admin/config/",
        HttpUrl.parse("http://localhost:3200/")!!
    ))
  }
}