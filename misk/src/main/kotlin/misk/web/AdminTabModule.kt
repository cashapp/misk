package misk.web

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceMapper

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
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<WebProxyAction>("/_admin"))
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<WebProxyAction>("/_tab/dashboard"))
//    multibind<WebActionEntry>().toInstance(
//        WebActionEntry<WebProxyAction>("/@misk"))

    if (environment == Environment.DEVELOPMENT) {
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_admin", "http://localhost:3100/"))

      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry("/_tab/dashboard", "http://localhost:3110/"))

//      multibind<WebProxyEntry>().toInstance(
//          WebProxyEntry("/@misk", "http://localhost:9100/"))
    } else {
      // Bind _admin static resources to web
      // TODO(adrw) need to only use StaticResourceMapper in production
      multibind<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/_admin/", "classpath:/web/_admin/"))
      multibind<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/_tab/dashboard/", "classpath:/web/_tab/dashboard/"))
      multibind<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/_tab/loader/", "classpath:/web/_tab/loader/"))
    }

//    Testing
//    multibind<AdminTab>().toInstance(AdminTab(
//        "Dashboard",
//        "dashboard",
//        "/_admin/dashboard"
//    ))

  }
}