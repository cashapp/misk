package misk.client

import com.google.inject.Key
import com.google.inject.Provider
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** Provides an [OkHttpClient] and [ProtoMessageHttpClient] for a peer service */
class HttpClientModule constructor(
  private val name: String,
  private val annotation: Annotation? = null,
  private val httpClientEndpointConfig: HttpClientEndpointConfig? = null
) : KAbstractModule() {
  override fun configure() {
    val httpClientKey =
        if (annotation == null) Key.get(OkHttpClient::class.java)
        else Key.get(OkHttpClient::class.java, annotation)
    val protoMessageHttpClientKey =
        if (annotation == null) Key.get(ProtoMessageHttpClient::class.java)
        else Key.get(ProtoMessageHttpClient::class.java, annotation)
    bind(httpClientKey)
        .toProvider(HttpClientProvider(name, httpClientEndpointConfig))
        .`in`(Singleton::class.java)
    bind(protoMessageHttpClientKey)
        .toProvider(ProtoMessageHttpClientProvider(
            name, getProvider(httpClientKey), httpClientEndpointConfig
        )).`in`(Singleton::class.java)
  }

  private class HttpClientProvider(
    private val name: String,
    private val httpClientEndpointConfig: HttpClientEndpointConfig?
  ) : Provider<OkHttpClient> {
    @Inject lateinit var httpClientsConfig: HttpClientsConfig
    @Inject lateinit var httpClientFactory: HttpClientFactory

    override fun get() = httpClientFactory.create(
        httpClientsConfig.getWithOverride(name, httpClientEndpointConfig)
    )
  }

  private class ProtoMessageHttpClientProvider(
    private val name: String,
    private val httpClientProvider: Provider<OkHttpClient>,
    private val httpClientEndpointConfig: HttpClientEndpointConfig?
  ) : Provider<ProtoMessageHttpClient> {
    @Inject lateinit var moshi: Moshi
    @Inject lateinit var httpClientsConfig: HttpClientsConfig
    @Inject lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

    override fun get(): ProtoMessageHttpClient {
      val endpointConfig = httpClientsConfig.getWithOverride(name, httpClientEndpointConfig)
      val httpClient = httpClientProvider.get()

      return ProtoMessageHttpClient(
          httpClientConfigUrlProvider.getUrl(endpointConfig), moshi, httpClient)
    }
  }
}
