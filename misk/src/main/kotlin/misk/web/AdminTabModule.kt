package misk.web

import misk.inject.KAbstractModule
import misk.web.actions.AdminTab
import misk.web.resources.WebProxyInterceptor
import okhttp3.HttpUrl

/**
 * AdminTabModule
 *
 * Binds the admin UI framework. Individual tabs should be bound with their other code.
 *
 * Example
 * Config tab is tightly coupled to the config module. Thus binding should be in ConfigWebModule
 */

class AdminTabModule : KAbstractModule() {
  override fun configure() {
    multibind<AdminTab>().toInstance(AdminTab(
        "Dashboard",
        "dashboard",
        "/_admin/dashboard/"
    ))
    multibind<WebProxyInterceptor.Mapping>().toInstance(
        WebProxyInterceptor.Mapping(
            "/_admin/dashboard/",
            HttpUrl.parse("http://localhost:3110/")!!
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