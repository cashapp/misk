package misk.web.metadata

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyAction
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class WebTabResourceModule(
  val environment: Environment,
  val slug: String,
  val web_proxy_url: String,
  val url_path_prefix: String = "/_tab/$slug/",
  val resourcePath: String = "classpath:/web/_tab/$slug/"
) : KAbstractModule() {
  override fun configure() {
    // Environment Dependent WebProxyAction or StaticResourceAction bindings
    multibind<StaticResourceEntry>()
        .toInstance(
            StaticResourceEntry(url_path_prefix = url_path_prefix, resourcePath = resourcePath))

    if (environment == Environment.DEVELOPMENT) {
      install(WebActionModule.forPrefixedAction<WebProxyAction>(url_path_prefix = url_path_prefix))
      multibind<WebProxyEntry>().toInstance(
          WebProxyEntry(url_path_prefix = url_path_prefix, web_proxy_url = web_proxy_url))
    } else {
      install(WebActionModule.forPrefixedAction<StaticResourceAction>(url_path_prefix = url_path_prefix))
      install(WebActionModule.forPrefixedAction<StaticResourceAction>(url_path_prefix = url_path_prefix))
    }
  }
}