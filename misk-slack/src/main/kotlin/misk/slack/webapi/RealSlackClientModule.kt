package misk.slack.webapi

import misk.client.TypedHttpClientModule
import misk.inject.KAbstractModule
import misk.slack.webapi.interceptors.SlackClientInterceptor
import misk.slack.webapi.interceptors.SlackSignedRequestsInterceptor
import misk.web.NetworkInterceptor
import okhttp3.Interceptor

class RealSlackClientModule(private val config: SlackConfig) : KAbstractModule() {
  override fun configure() {
    install(TypedHttpClientModule.create<SlackApi>("slack"))
    multibind<Interceptor>().to<SlackClientInterceptor>()
    bind<SlackClient>().to<RealSlackClient>()
    bind<SlackConfig>().toInstance(config)
    multibind<NetworkInterceptor.Factory>().to<SlackSignedRequestsInterceptor.Factory>()
  }
}
