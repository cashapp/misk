package misk.web

import misk.inject.KAbstractModule
import misk.web.actions.AdminTab

class AdminTabModule : KAbstractModule() {
  override fun configure() {
    // only bind tabs here that do not consume any endpoint
    // otherwise put the binding with where those endpoints are
    // ie. config tab uses data from endpoint within config, therefore binding is in ConfigWebModule
    multibind<AdminTab>().toInstance(AdminTab(
        "Dashboard",
        "dashboard",
        "/_admin/dashboard/"
    ))
    multibind<AdminTab>().toInstance(AdminTab(
        "Misk NPM",
        "@misk",
        "/_admin/@misk/"
    ))
    multibind<AdminTab>().toInstance(AdminTab(
        "Loader",
        "loader",
        "/_admin/"
    ))
    multibind<AdminTab>().toInstance(AdminTab(
        "Temp Test",
        "test",
        "/_admin/test/"
    ))
  }
}