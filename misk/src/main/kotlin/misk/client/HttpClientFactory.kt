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

  @Inject private lateinit var clientMetricsInterceptorFactory: ClientMetricsInterceptor.Factory

  @com.google.inject.Inject(optional = true)
  var envoyClientEndpointProvider: EnvoyClientEndpointProvider? = null

  @com.google.inject.Inject(optional = true)
  var okhttpInterceptors: Provider<List<Interceptor>>? = null

  /**
   * Returns a client initialized based on `config`.
   * @param config the configuration for the client
   * @param serviceName the name of the target service, used for client side metrics.
   * If null, no metrics will be collected.
   */
  @JvmOverloads
  fun create(config: HttpClientEndpointConfig, serviceName: String? = null): OkHttpClient {

    val interceptors = mutableListOf<Interceptor>()
    if (okhttpInterceptors != null) {
      interceptors.addAll(okhttpInterceptors!!.get())
    }
    interceptors.add(clientLoggingInterceptor)
    if (serviceName != null) {
      interceptors.add(clientMetricsInterceptorFactory.create(serviceName))
    }

    val delegate = wisp.client.HttpClientFactory(
      sslLoader = sslLoader.delegate,
      sslContextFactory = sslContextFactory.delegate,
      okHttpClientCommonConfigurator = okHttpClientCommonConfigurator.delegate,
      envoyClientEndpointProvider = envoyClientEndpointProvider,
      okhttpInterceptors = interceptors.toList()
    )

    val okHttpClient = delegate.create(config.toWispConfig())
    return okHttpClient
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
  }
}
