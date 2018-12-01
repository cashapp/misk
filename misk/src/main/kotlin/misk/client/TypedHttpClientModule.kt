package misk.client

import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.Provider
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import misk.inject.KAbstractModule
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/** Creates a retrofit-backed typed client given an API interface and an HTTP configuration */
class TypedHttpClientModule<T : Any>(
  private val kclass: KClass<T>,
  private val name: String,
  private val annotation: Annotation? = null
) : KAbstractModule() {
  override fun configure() {
    // Initialize empty sets for our multibindings.
    newMultibinder<ClientNetworkInterceptor.Factory>()
    newMultibinder<ClientApplicationInterceptor.Factory>()

    // Install raw HTTP client support
    install(HttpClientModule(name, annotation))

    val httpClientKey = if (annotation == null) Key.get(OkHttpClient::class.java)
    else Key.get(OkHttpClient::class.java, annotation)

    val httpClientProvider = binder().getProvider(httpClientKey)
    val key = if (annotation == null) Key.get(kclass.java) else Key.get(kclass.java, annotation)
    bind(key).toProvider(TypedClientProvider(kclass, name, httpClientProvider))
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
    private val kclass: KClass<T>,
    private val name: String,
    private val httpClientProvider: Provider<OkHttpClient>
  ) : Provider<T> {
    @Inject
    private lateinit var httpClientsConfig: HttpClientsConfig

    @Inject
    private lateinit var clientNetworkInterceptorFactories: List<ClientNetworkInterceptor.Factory>

    @Inject
    private lateinit var clientApplicationInterceptorFactories: List<ClientApplicationInterceptor.Factory>

    @Inject
    private lateinit var moshi: Moshi

    @Inject(optional = true)
    private val tracer: Tracer? = null

    @Inject
    private lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

    override fun get(): T {
      val okhttp = httpClientProvider.get()
      val endpointConfig = httpClientsConfig[name]
      val retrofit = Retrofit.Builder()
          .baseUrl(httpClientConfigUrlProvider.getUrl(endpointConfig))
          .build()

      val invocationHandler = ClientInvocationHandler(
          kclass,
          name,
          retrofit,
          okhttp,
          clientNetworkInterceptorFactories,
          clientApplicationInterceptorFactories,
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
}
