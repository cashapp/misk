package misk.client

import com.google.inject.Key
import com.google.inject.name.Names
import com.squareup.wire.Service
import jakarta.inject.Singleton
import kotlin.reflect.KClass
import misk.inject.KAbstractModule
import okhttp3.OkHttpClient

/** Creates a gRPC client given a Wire-generated interface and HTTP configuration. */
class GrpcClientModule<T : Service, G : T>
@JvmOverloads
constructor(
  /** The Wire-generated service interface. */
  private val kclass: KClass<T>,

  /** The gRpc client implementation of the service interface */
  private val grpcClientClass: KClass<G>,

  /** Name of the OkHttpClient in the application's [HttpClientsConfig]. */
  private val name: String,

  /** Qualifier annotation on the bound service. If null the service will be bound unannotated. */
  private val annotation: Annotation? = null,

  /**
   * Sets the minimum outbound message size (in bytes) that will be compressed.
   *
   * Set this to 0 to enable compression for all outbound messages. Set to [Long.MAX_VALUE] to disable compression.
   *
   * This is 0 by default.
   */
  private val minMessageToCompress: Long = 0L,
) : KAbstractModule() {
  private val httpClientAnnotation = annotation ?: Names.named(kclass.qualifiedName)

  override fun configure() {
    install(HttpClientModule(name, httpClientAnnotation))

    val httpClientKey = Key.get(OkHttpClient::class.java, httpClientAnnotation)
    val httpClientProvider = binder().getProvider(httpClientKey)

    val key = if (annotation == null) Key.get(kclass.java) else Key.get(kclass.java, annotation)
    bind(key)
      .toProvider(GrpcClientProvider(kclass, grpcClientClass, name, httpClientProvider, minMessageToCompress))
      .`in`(Singleton::class.java)

    // Initialize empty sets for our multibindings.
    newMultibinder<ClientApplicationInterceptorFactory>()
    newMultibinder<ClientNetworkInterceptor.Factory>()
    newMultibinder<CallFactoryWrapper>()
  }

  companion object {
    inline fun <reified T : Service, reified G : T> create(name: String, annotation: Annotation? = null) =
      GrpcClientModule(T::class, G::class, name, annotation)
  }
}
