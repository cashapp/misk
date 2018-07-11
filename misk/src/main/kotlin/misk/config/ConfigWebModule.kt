package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.actions.RegisteredTab
import okhttp3.HttpUrl

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigAdminAction>())
    multibind<RegisteredTab>().toInstance(RegisteredTab(
        "/_admin/config/",
        HttpUrl.parse("http://localhost:3200/")!!,
        "Config",
        "config",
        "",
        "/web/tabs/config/"
      )
    )
  }
}