package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.actions.AdminTab
import misk.web.actions.UpstreamResourceInterceptor
import okhttp3.HttpUrl

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigAdminAction>())
    multibind<AdminTab>().toInstance(AdminTab(
        "Config",
        "config",
        "/_admin/config/"
    ))
    multibind<UpstreamResourceInterceptor.Mapping>().toInstance(UpstreamResourceInterceptor.Mapping(
        "/_admin/config/",
        HttpUrl.parse("http://localhost:3200/")!!,
        "/web/tabs/config/",
        UpstreamResourceInterceptor.Mode.SERVER
    ))
  }
}