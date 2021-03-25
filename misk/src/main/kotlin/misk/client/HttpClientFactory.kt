package misk.client

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import wisp.client.EnvoyClientEndpointProvider
import wisp.client.OkHttpClientCommonConfigurator
import wisp.security.ssl.SslContextFactory
import wisp.security.ssl.SslLoader
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class HttpClientFactory @Inject constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory,
  private val okHttpClientCommonConfigurator: OkHttpClientCommonConfigurator
) {
  @com.google.inject.Inject(optional = true)
  var envoyClientEndpointProvider: EnvoyClientEndpointProvider? = null

  @com.google.inject.Inject(optional = true)
  var okhttpInterceptors: Provider<List<Interceptor>>? = null

  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient {
    val delegate = wisp.client.HttpClientFactory(
      sslLoader,
      sslContextFactory,
      okHttpClientCommonConfigurator,
      envoyClientEndpointProvider,
      okhttpInterceptors?.get()
    )

    return delegate.create(config.toWispConfig())
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
