package misk.client

import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.name.Names
import com.google.inject.util.Types
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import misk.clustering.Cluster
import misk.inject.KAbstractModule
import okhttp3.EventListener
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.lang.reflect.Proxy
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Creates a retrofit-backed typed client given an API interface and an HTTP configuration.
 *
 * @param retrofitBuilderProvider Optional provider of a [Retrofit.Builder]. This provider should
 * not return a singleton since the builder it returns will be mutated.
 * @param httpClientEndpointConfig Optional override for endpoint config. This is useful if you want
 * to bind up a client that isn't otherwise present in your HttpClientsConfig.
 */
class TypedHttpClientModule<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val annotation: Annotation? = null,
  private val retrofitBuilderProvider: Provider<Retrofit.Builder>? = null,
  private val httpClientEndpointConfig: HttpClientEndpointConfig? = null
) : KAbstractModule() {
  private val httpClientAnnotation = annotation ?: Names.named(kclass.qualifiedName)

  override fun configure() {
    // Initialize empty sets for our multibindings.
    newMultibinder<ClientNetworkInterceptor.Factory>()
    newMultibinder<ClientApplicationInterceptor.Factory>()

    // Install raw HTTP client support
    install(HttpClientModule(name, httpClientAnnotation, httpClientEndpointConfig))

    val httpClientKey = Key.get(OkHttpClient::class.java, httpClientAnnotation)

    val httpClientProvider = binder().getProvider(httpClientKey)
    val key = if (annotation == null) Key.get(kclass.java) else Key.get(kclass.java, annotation)
    bind(key)
        .toProvider(TypedClientProvider(
            kclass, name, httpClientProvider, retrofitBuilderProvider, httpClientEndpointConfig
        )).`in`(Singleton::class.java)
  }

  companion object {
    inline fun <reified T : Any> create(
      name: String,
      annotation: Annotation? = null,
      httpClientEndpointConfig: HttpClientEndpointConfig? = null
    ): TypedHttpClientModule<T> {
      return TypedHttpClientModule(
          kclass = T::class,
          name = name,
          annotation = annotation,
          retrofitBuilderProvider = null,
          httpClientEndpointConfig = httpClientEndpointConfig
      )
    }
  }

  private class TypedClientProvider<T : Any>(
    kclass: KClass<T>,
    private val name: String,
    private val httpClientProvider: Provider<OkHttpClient>,
    retrofitBuilderProvider: Provider<Retrofit.Builder>?,
    val httpClientEndpointConfig: HttpClientEndpointConfig?
  ) : TypedClientFactory<T>(kclass, name, retrofitBuilderProvider), Provider<T> {

    @Inject private lateinit var httpClientsConfig: HttpClientsConfig
    @Inject private lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

    override fun get(): T {
      val client = httpClientProvider.get()
      val endpointConfig = httpClientsConfig.getWithOverride(name, httpClientEndpointConfig)
      val baseUrl = httpClientConfigUrlProvider.getUrl(endpointConfig)

      return typedClient(client, baseUrl)
    }
  }
}

/**
 * Factory for creating typed clients that call other members of a cluster.
 */
interface TypedPeerClientFactory<T> {
  fun client(peer: Cluster.Member): T
}

/**
 * Creates a retrofit-backed typed client factory given an API interface and an HTTP configuration.
 *
 * The factory returned typed clients that can be used to call other members of the cluster.
 */
class TypedPeerHttpClientModule<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val retrofitBuilderProvider: Provider<Retrofit.Builder>? = null
) : KAbstractModule() {

  override fun configure() {
    requireBinding(PeerClientFactory::class.java)

    // Initialize empty sets for our multibindings.
    newMultibinder<ClientNetworkInterceptor.Factory>()
    newMultibinder<ClientApplicationInterceptor.Factory>()

    @Suppress("UNCHECKED_CAST")
    val key = Key.get(
        Types.newParameterizedType(TypedPeerClientFactory::class.java,
            kclass.java)) as Key<TypedPeerClientFactory<T>>

    bind(key)
        .toProvider(PeerTypedClientProvider(kclass, name, retrofitBuilderProvider))
        .`in`(Singleton::class.java)
  }

  companion object {
    inline fun <reified T : Any> create(name: String): TypedPeerHttpClientModule<T> {
      return TypedPeerHttpClientModule(T::class, name)
    }
  }

  private class PeerTypedClientProvider<T : Any>(
    kclass: KClass<T>,
    name: String,
    retrofitBuilderProvider: Provider<Retrofit.Builder>?
  ) : TypedClientFactory<T>(kclass, name, retrofitBuilderProvider),
      Provider<TypedPeerClientFactory<T>> {

    @Inject private lateinit var peerClientFactory: PeerClientFactory

    override fun get(): TypedPeerClientFactory<T> {
      return object : TypedPeerClientFactory<T> {
        override fun client(peer: Cluster.Member): T {
          return typedClient(peerClientFactory.client(peer), peerClientFactory.baseUrl(peer))
        }
      }
    }
  }
}

private abstract class TypedClientFactory<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val retrofitBuilderProvider: Provider<Retrofit.Builder>?
) {

  // Use Providers for the interceptors so Guice can properly detect cycles when apps inject
  // an HTTP Client in an Interceptor.
  // https://gist.github.com/ryanhall07/e3eac6d2d47b72a4c37bce87219d7ced
  @Inject
  private lateinit var clientNetworkInterceptorFactories: Provider<List<ClientNetworkInterceptor.Factory>>

  @Inject
  private lateinit var clientApplicationInterceptorFactories: Provider<List<ClientApplicationInterceptor.Factory>>

  @Inject
  private lateinit var moshi: Moshi

  @Inject(optional = true)
  private val tracer: Tracer? = null

  @Inject(optional = true)
  private val eventListenerFactory: EventListener.Factory? = null

  fun typedClient(client: OkHttpClient, baseUrl: String): T {
    val retrofit = (retrofitBuilderProvider?.get() ?: Retrofit.Builder())
        .baseUrl(baseUrl)
        .build()

    val invocationHandler = ClientInvocationHandler(
        kclass,
        name,
        retrofit,
        client,
        clientNetworkInterceptorFactories,
        clientApplicationInterceptorFactories,
        eventListenerFactory,
        tracer,
        moshi)

    @Suppress("UNCHECKED_CAST")
    return Proxy.newProxyInstance(
        ClassLoader.getSystemClassLoader(),
        arrayOf(kclass.java),
        invocationHandler
    ) as T
  }
}
