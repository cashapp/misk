package misk.client

import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import wisp.client.EnvoyClientEndpointProvider
import jakarta.inject.Inject
import com.google.inject.Provider
import jakarta.inject.Singleton

@Singleton
class HttpClientFactory @Inject constructor(
  private val sslLoader: SslLoader,
  private val sslContextFactory: SslContextFactory,
  private val okHttpClientCommonConfigurator: OkHttpClientCommonConfigurator,
) {
  // Field-injected so ClientLoggingInterceptor remains internal.
  @Inject private lateinit var clientLoggingInterceptor: ClientLoggingInterceptor

  @com.google.inject.Inject(optional = true)
  var envoyClientEndpointProvider: EnvoyClientEndpointProvider? = null

  @com.google.inject.Inject(optional = true)
  var okhttpInterceptors: Provider<List<Interceptor>>? = null

  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient {
    val delegate = wisp.client.HttpClientFactory(
      sslLoader.delegate,
      sslContextFactory.delegate,
      okHttpClientCommonConfigurator.delegate,
      envoyClientEndpointProvider,
      okhttpInterceptors?.let { it.get() + clientLoggingInterceptor }
        ?: listOf(clientLoggingInterceptor)
    )

    val okHttpClient = delegate.create(config.toWispConfig())
    return okHttpClient
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
