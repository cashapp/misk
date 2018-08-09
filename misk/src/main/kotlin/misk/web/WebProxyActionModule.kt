package misk.web

import com.google.inject.name.Names
import misk.client.HttpClientModule
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyEntry

/** Intercept web requests and route to webpack dev servers */
class WebProxyActionModule : KAbstractModule() {
  override fun configure() {
    newMultibinder<WebProxyEntry>()
    newMultibinder<WebActionEntry>()
    install(HttpClientModule("web_proxy_action", Names.named("web_proxy_action")))
  }
}