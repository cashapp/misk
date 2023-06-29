package `slack-api`

import misk.client.TypedHttpClientModule
import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import okhttp3.Interceptor

class RealSlackClientModule(
  private val config: SlackConfig,
) : KAbstractModule() {
  override fun configure() {
    install(TypedHttpClientModule.create<SlackApi>("slack-api"))
    multibind<Interceptor>().to<SlackClientInterceptor>()
    bind<SlackClient>().to<RealSlackClient>()
    bind<SlackConfig>().toInstance(config)

    multibind<NetworkInterceptor.Factory>().to<SlackSignedRequestsInterceptor.Factory>()
  }
}
