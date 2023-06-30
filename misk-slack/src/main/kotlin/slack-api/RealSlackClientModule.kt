package `slack-api`

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.client.TypedHttpClientModule
import misk.inject.KAbstractModule
import misk.slack.SlackWebhookApi
import misk.web.NetworkInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import wisp.moshi.buildMoshi
import javax.inject.Named
import javax.inject.Singleton

class RealSlackClientModule(
  private val config: SlackConfig,
) : KAbstractModule() {
  override fun configure() {
    multibind<Interceptor>().to<SlackClientInterceptor>()
    bind<SlackClient>().to<RealSlackClient>()
    bind<SlackConfig>().toInstance(config)
    multibind<NetworkInterceptor.Factory>().to<SlackSignedRequestsInterceptor.Factory>()
  }

  @Provides @Singleton fun providesSlackApi(
    httpClientFactory: HttpClientFactory,
    @Named("slack-api") moshi: Moshi
  ): SlackApi {
    val okHttpClient = httpClientFactory.create(
      HttpClientEndpointConfig(url = config.url)
    )
    val retrofit = Retrofit.Builder()
      .baseUrl(config.url)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .client(okHttpClient)
      .build()
    return retrofit.create(SlackApi::class.java)
  }

  @Provides @Singleton @Named("slack-api") fun provideMoshi(): Moshi {
    return buildMoshi(emptyList())
  }
}
