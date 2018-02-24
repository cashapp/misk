package misk.client

import com.google.inject.Key
import com.google.inject.Provider
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import okhttp3.OkHttpClient
import javax.inject.Inject

/** Provides an [OkHttpClient] and [ProtoMessageHttpClient] for a peer service */
// TODO(mmihic): Replace with something that binds a typed client via retrofit
class HttpClientModule constructor(
    private val name: String,
    private val annotation: Annotation? = null
) : KAbstractModule() {
  override fun configure() {
    val httpClientKey =
        if (annotation == null) Key.get(OkHttpClient::class.java)
        else Key.get(OkHttpClient::class.java, annotation)
    val protoMessageHttpClientKey =
        if (annotation == null) Key.get(ProtoMessageHttpClient::class.java)
        else Key.get(ProtoMessageHttpClient::class.java, annotation)
    bind(httpClientKey).toProvider(HttpClientProvider(name))
    bind(protoMessageHttpClientKey)
        .toProvider(ProtoMessageHttpClientProvider(name, getProvider(httpClientKey)))
  }

  private class HttpClientProvider(private val name: String) : Provider<OkHttpClient> {
    @Inject
    lateinit var httpClientConfig: HttpClientConfig

    override fun get() = httpClientConfig.newHttpClient(name)
  }

  private class ProtoMessageHttpClientProvider(
      private val name: String,
      private val httpClientProvider: Provider<OkHttpClient>
  ) : Provider<ProtoMessageHttpClient> {
    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var httpClientConfig: HttpClientConfig

    override fun get(): ProtoMessageHttpClient {
      val endpointConfig = httpClientConfig.endpoints[name] ?: throw IllegalArgumentException(
          "no client configuration for endpoint $name")
      val httpClient = httpClientProvider.get()
      return ProtoMessageHttpClient(endpointConfig.url, moshi, httpClient)
    }
  }
}
