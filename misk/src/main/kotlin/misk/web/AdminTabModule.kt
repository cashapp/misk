package misk.web

import misk.inject.KAbstractModule
import misk.web.actions.RegisteredTab
import okhttp3.HttpUrl

class AdminTabModule : KAbstractModule() {
  override fun configure() {
    // only bind tabs here that do not consume any endpoint
    // otherwise put the binding with where those endpoints are
    // ie. config tab uses data from endpoint within config, therefore binding is in ConfigWebModule
    multibind<RegisteredTab>().toInstance(RegisteredTab(
        "/_admin/dashboard/",
        HttpUrl.parse("http://localhost:3110/")!!,
        "Dashboard",
        "dashboard",
        "",
        "/web/tabs/dashboard/"
      )
    )
    multibind<RegisteredTab>().toInstance(RegisteredTab(
        "/_admin/@misk/",
        HttpUrl.parse("http://localhost:9100/")!!,
        "Misk NPM",
        "@misk",
        "",

        "/web/tabs/@misk/"
      )
    )
    multibind<RegisteredTab>().toInstance(RegisteredTab(
        "/_admin/",
        HttpUrl.parse("http://localhost:3100/")!!,"Loader",
        "loader",
        "",
        "/web/tabs/loader/"
      )
    )
    multibind<RegisteredTab>().toInstance(RegisteredTab(
        "/_admin/test/",
        HttpUrl.parse("http://localhost:8000/")!!,
        "Temp Test",
        "test",
        "",
        "/web/tabs/test/"
      )
    )
  }
}