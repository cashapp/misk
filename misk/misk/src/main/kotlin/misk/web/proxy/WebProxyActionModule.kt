package misk.web.proxy

import com.google.inject.name.Names
import misk.client.HttpClientModule
import misk.inject.KAbstractModule

/** Intercept web requests and route to webpack dev servers */
class WebProxyActionModule : KAbstractModule() {
  override fun configure() {
    install(HttpClientModule("web_proxy_action", Names.named("web_proxy_action")))
  }
}