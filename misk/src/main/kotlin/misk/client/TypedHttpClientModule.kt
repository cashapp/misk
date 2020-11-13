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
import kotlin.reflect.cast

/**
 * Creates a retrofit-backed typed client given an API interface and an HTTP configuration.
 *
 * @param retrofitBuilderProvider Optional provider of a [Retrofit.Builder]. This provider should
 * not return a singleton since the builder it returns will be mutated.
 */
class TypedHttpClientModule<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val annotation: Annotation? = null,
  private val retrofitBuilderProvider: Provider<Retrofit.Builder>? = null
) : KAbstractModule() {
  private val httpClientAnnotation = annotation ?: Names.named(kclass.qualifiedName)

  override fun configure() {
    // Initialize empty sets for our multibindings.
    newMultibinder<ClientNetworkInterceptor.Factory>()
    newMultibinder<ClientApplicationInterceptor.Factory>()

    // Install raw HTTP client support
    install(HttpClientModule(name, httpClientAnnotation))

    val httpClientKey = Key.get(OkHttpClient::class.java, httpClientAnnotation)
    val httpClientProvider = binder().getProvider(httpClientKey)

    val key = if (annotation == null) Key.get(kclass.java) else Key.get(kclass.java, annotation)
    bind(key)
        .toProvider(TypedClientProvider(kclass, name, httpClientProvider, retrofitBuilderProvider))
        .`in`(Singleton::class.java)
  }

  companion object {
    inline fun <reified T : Any> create(
      name: String,
      annotation: Annotation? = null
    ): TypedHttpClientModule<T> {
      return TypedHttpClientModule(T::class, name, annotation)
    }
  }

  private class TypedClientProvider<T : Any>(
    kclass: KClass<T>,
    private val name: String,
    private val httpClientProvider: Provider<OkHttpClient>,
    retrofitBuilderProvider: Provider<Retrofit.Builder>?
  ) : TypedClientFactoryProvider<T>(kclass, name, retrofitBuilderProvider), Provider<T> {

    @Inject private lateinit var httpClientsConfig: HttpClientsConfig
    @Inject private lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

    override fun get(): T {
      val client = httpClientProvider.get()
      val endpointConfig = httpClientsConfig[name]
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
  ) : TypedClientFactoryProvider<T>(kclass, name, retrofitBuilderProvider),
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

class TypedClientFactory @Inject constructor() {
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

  @Inject private lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

  @Inject private lateinit var httpClientFactory: HttpClientFactory

  /**
   * Build up a typed client dynamically in runtime. This is useful for platform-type services
   * that cannot statically define all of the services they talk to.
   *
   * Services should cache the resulting clients to avoid incurring the construction on every call.
   *
   * @param endpointConfig HTTP configuration to use to connect to the service
   * @param kclass The class of the typed client that will be built
   * @param name A name to reference the client by for observability purposes
   * @param retrofitBuilderProvider Optional retrofit builder override.
   *        If not provided, an empty builder is used
   */
  fun <T : Any> build(
    endpointConfig: HttpClientEndpointConfig,
    kclass: KClass<T>,
    name: String,
    retrofitBuilderProvider: Provider<Retrofit.Builder>?
  ): T {
    val baseUrl = httpClientConfigUrlProvider.getUrl(endpointConfig)
    val client = httpClientFactory.create(endpointConfig)

    return typedClient(client, baseUrl, kclass, name, retrofitBuilderProvider)
  }

  /** Reified flavor of build */
  inline fun <reified T : Any> build(
    endpointConfig: HttpClientEndpointConfig,
    name: String,
    retrofitBuilderProvider: Provider<Retrofit.Builder>? = null
  ): T {
    return build(endpointConfig, T::class, name, retrofitBuilderProvider)
  }

  internal fun <T : Any> typedClient(
    client: OkHttpClient,
    baseUrl: String,
    kclass: KClass<T>,
    name: String,
    retrofitBuilderProvider: Provider<Retrofit.Builder>?): T {
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

    return kclass.cast(Proxy.newProxyInstance(
        ClassLoader.getSystemClassLoader(),
        arrayOf(kclass.java),
        invocationHandler
    ))
  }
}

private abstract class TypedClientFactoryProvider<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val retrofitBuilderProvider: Provider<Retrofit.Builder>?
) {

  @Inject private lateinit var typedClientFactory: TypedClientFactory

  fun typedClient(client: OkHttpClient, baseUrl: String): T {
    return typedClientFactory.typedClient(client, baseUrl, kclass, name, retrofitBuilderProvider)
  }
}
