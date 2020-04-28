package misk.client

import com.google.inject.Inject
import com.google.inject.Key
import com.google.inject.name.Names
import com.squareup.wire.GrpcClient
import com.squareup.wire.Service
import misk.inject.KAbstractModule
import okhttp3.OkHttpClient
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Creates a gRPC client given a Wire-generated interface and HTTP configuration.
 */
class GrpcClientModule<T : Service, G : T>(
  /** The Wire-generated service interface. */
  private val kclass: KClass<T>,

  /** The gRpc client implementation of the service interface */
  private val grpcClientClass: KClass<G>,

  /** Name of the OkHttpClient in the application's [HttpClientsConfig]. */
  private val name: String,

  /** Qualifier annotation on the bound service. If null the service will be bound unannotated. */
  private val annotation: Annotation? = null
) : KAbstractModule() {
  private val httpClientAnnotation = annotation ?: Names.named(kclass.qualifiedName)

  override fun configure() {
    install(HttpClientModule(name, httpClientAnnotation))

    val httpClientKey = Key.get(OkHttpClient::class.java, httpClientAnnotation)
    val httpClientProvider = binder().getProvider(httpClientKey)

    val key = if (annotation == null) Key.get(kclass.java) else Key.get(kclass.java, annotation)
    bind(key)
        .toProvider(GrpcClientProvider(grpcClientClass, name, httpClientProvider))
        .`in`(Singleton::class.java)
  }

  companion object {
    inline fun <reified T : Service, reified G : T> create(
      name: String,
      annotation: Annotation? = null
    ) = GrpcClientModule(T::class, G::class, name, annotation)
  }

  private class GrpcClientProvider<T : Service, G : T>(
    private val grpcClientClass: KClass<G>,
    private val name: String,
    private val httpClientProvider: Provider<OkHttpClient>
  ) : Provider<T> {
    /** Use a provider because we don't know the test client's URL until its test server starts. */
    @Inject private lateinit var httpClientsConfigProvider: Provider<HttpClientsConfig>
    @Inject private lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

    override fun get(): T {
      val client = httpClientProvider.get()
      val endpointConfig = httpClientsConfigProvider.get()[name]
      val baseUrl = httpClientConfigUrlProvider.getUrl(endpointConfig)

      val grpcClient = GrpcClient.Builder()
          .client(client)
          .baseUrl(baseUrl)
          .build()

      // There should be *exactly one constructor* that takes in a grpcClient
      return grpcClientClass.constructors.first().call(grpcClient)
    }
  }
}
