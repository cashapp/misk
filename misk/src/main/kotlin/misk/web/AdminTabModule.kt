package misk.web

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

/**
 * AdminTabModule
 *
 * Binds the admin UI framework. Individual tabs should be bound with their other code.
 *
 * Example
 * Config tab is tightly coupled to the config module. Thus binding should be in ConfigWebModule
 */

class AdminTabModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {


    if (environment == Environment.DEVELOPMENT) {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_admin"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/_tab/dashboard"))
//    multibind<WebActionEntry>().toInstance(
//        WebActionEntry<WebProxyAction>("/@misk"))

      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_admin", "http://localhost:3100/"))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_tab/dashboard", "http://localhost:3110/"))
//      multibind<WebProxyEntry>().toInstance(
//          WebProxyEntry("/@misk", "http://localhost:9100/"))
    } else {
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_admin"))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<StaticResourceAction>("/_tab/dashboard"))

      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/_admin", "classpath:/web/_admin/"))
      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/_tab/dashboard", "classpath:/web/_tab/dashboard/"))
//      multibind<StaticResourceEntry>()
//          .toInstance(StaticResourceEntry("/_tab/loader/", "classpath:/web/_tab/loader/"))
    }

//    Testing
//    multibind<AdminTab>().toInstance(AdminTab(
//        "Dashboard",
//        "dashboard",
//        "/_admin/dashboard"
//    ))

  }
}