package misk.slack

import com.google.inject.Provides
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.inject.KAbstractModule
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Installs the Slack webhook client.
 * This should be installed once per service and enables imported libraries to post to Slack using
 * the service level config.
 */
class SlackModule(private val config: SlackConfig) : KAbstractModule() {
  private val baseUrl = "https://hooks.slack.com"

  override fun configure() {
    bind<SlackConfig>().toInstance(config)
    bind<SlackClient>().to(RealSlackClient::class.java)
  }

  @Provides @Singleton fun provideSlackWebhookApi(
    httpClientFactory: HttpClientFactory
  ): SlackWebhookApi {
    val okHttpClient = httpClientFactory.create(
        HttpClientEndpointConfig(url = baseUrl))
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(okHttpClient)
        .build()
    return retrofit.create(SlackWebhookApi::class.java)
  }
}