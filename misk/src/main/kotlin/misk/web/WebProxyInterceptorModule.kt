package misk.web

import com.google.inject.name.Names
import misk.client.HttpClientModule
import misk.inject.KAbstractModule
import misk.web.resources.WebProxyInterceptor

/** Intercept web requests and route to webpack dev servers */
class WebProxyInterceptorModule : KAbstractModule() {
  override fun configure() {
    newMultibinder<WebProxyInterceptor.Mapping>()
    install(HttpClientModule("web_proxy_interceptor",
        Names.named("web_proxy_interceptor")))
    multibind<NetworkInterceptor.Factory>()
        .to<WebProxyInterceptor.Factory>()
  }
}