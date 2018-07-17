package misk.web

import com.google.inject.name.Names
import misk.client.HttpClientModule
import misk.inject.KAbstractModule
import misk.web.actions.WebProxyAction

/** Intercept web requests and route to webpack dev servers */
class WebProxyActionModule : KAbstractModule() {
  override fun configure() {
    newMultibinder<WebProxyAction.Mapping>()
    install(HttpClientModule("web_proxy_interceptor",
        Names.named("web_proxy_interceptor")))
    multibind<NetworkInterceptor.Factory>()
        .to<WebProxyAction.Factory>()
  }
}