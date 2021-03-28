package misk.client

import okhttp3.OkHttpClient
import wisp.client.HttpClientEndpointConfig
import javax.inject.Inject

class OkHttpClientCommonConfigurator @Inject constructor() {
  val delegate = wisp.client.OkHttpClientCommonConfigurator()

  fun configure(
    builder: OkHttpClient.Builder,
    config: HttpClientEndpointConfig
  ): OkHttpClient.Builder = delegate.configure(builder, config)
}
