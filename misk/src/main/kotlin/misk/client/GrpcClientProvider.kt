package misk.client

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcStreamingCall
import com.squareup.wire.Service
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.kotlinFunction

/**
 * Creates an instance of a Wire gRPC client using Misk's HTTP configuration and client
 * interceptors.
 *
 * This assumes a Wire-generated service interface (`T`) like the following:
 *
 * ```
 * interface RouteGuideClient : Service {
 *   fun GetFeature(): GrpcCall<Point, Feature>
 *   fun RouteChat(): GrpcStreamingCall<RouteNote, RouteNote>
 * }
 * ```
 *
 * It also assumes a Wire-generated service implementation (`G`) like the following:
 *
 * ```
 * class GrpcRouteGuideClient(
 *   private val client: GrpcClient
 * ) : RouteGuideClient {
 *   override fun GetFeature(): GrpcCall<Point, Feature> {...}
 *   override fun RouteChat(): GrpcStreamingCall<RouteNote, RouteNote> {...}
 * }
 * ```
 *
 * It creates an [OkHttpClient] and [GrpcClient] following Misk's configuration. But instead of
 * creating a single instance of the `G` type, it creates a different instance per method, each with
 * its own interceptor stack. Then it aggregates this set with a dynamic proxy.
 */
internal class GrpcClientProvider<T : Service, G : T>(
  private val kclass: KClass<T>,
  private val grpcClientClass: KClass<G>,
  private val name: String,
  private val httpClientProvider: Provider<OkHttpClient>
) : Provider<T> {
  /** Use a provider because we don't know the test client's URL until its test server starts. */
  @Inject private lateinit var httpClientsConfigProvider: Provider<HttpClientsConfig>
  @Inject private lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

  @Inject
  private lateinit var interceptorFactories: Provider<List<ClientNetworkInterceptor.Factory>>
  @Inject private lateinit var clientMetricsInterceptorFactory: ClientMetricsInterceptor.Factory

  override fun get(): T {
    val endpointConfig: HttpClientEndpointConfig = httpClientsConfigProvider.get()[name]
    val baseUrl = httpClientConfigUrlProvider.getUrl(endpointConfig)
    val httpClient = httpClientProvider.get()
    return get(
      baseUrl = baseUrl,
      httpClient = httpClient,
      interceptorFactories = interceptorFactories.get()
    )
  }

  fun get(
    baseUrl: String,
    httpClient: OkHttpClient,
    interceptorFactories: List<ClientNetworkInterceptor.Factory>
  ): T {
    // Since gRPC uses HTTP/2, force h2c when calling an unencrypted endpoint
    val protocols = when {
      baseUrl.startsWith("http://") -> listOf(Protocol.H2_PRIOR_KNOWLEDGE)
      else -> listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
    }

    val clientPrototype = httpClient.newBuilder()
      .protocols(protocols)
      .build()

    val handlers = mutableMapOf<String, MethodInvocationHandler<T, G>>()
    for (method in kclass.java.methods) {
      val handler = methodHandler(method, clientPrototype, baseUrl, interceptorFactories)
        ?: continue
      handlers[method.name] = handler
    }

    val invocationHandler = object : InvocationHandler {
      override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
        val args = args ?: arrayOf()
        val handler = handlers[method.name]
        return if (handler != null) {
          handler.method.invoke(handler.delegate, *args)
        } else {
          method.invoke(this, *args) // equals(), hashCode(), etc.
        }
      }

      override fun toString() = "GrpcClient:${kclass.qualifiedName}"
    }

    return kclass.cast(
      Proxy.newProxyInstance(
        ClassLoader.getSystemClassLoader(),
        arrayOf(kclass.java),
        invocationHandler
      )
    )
  }

  private fun toClientAction(method: Method): ClientAction? {
    if (method.returnType !== GrpcCall::class.java &&
      method.returnType !== GrpcStreamingCall::class.java
    ) {
      return null
    }

    val genericReturnType = method.genericReturnType as? ParameterizedType ?: return null
    val requestType = genericReturnType.actualTypeArguments[0] as Class<*>
    val responseType = genericReturnType.actualTypeArguments[1] as Class<*>

    val kotlinFunction = method.kotlinFunction ?: return null

    return ClientAction(
      name = "$name.${method.name}",
      function = kotlinFunction,
      parameterTypes = listOf(requestType.kotlin.createType()),
      returnType = responseType.kotlin.createType()
    )
  }

  private fun methodHandler(
    method: Method,
    clientPrototype: OkHttpClient,
    baseUrl: String,
    interceptorFactories: List<ClientNetworkInterceptor.Factory>
  ): MethodInvocationHandler<T, G>? {
    val action = toClientAction(method) ?: return null

    val clientBuilder = clientPrototype.newBuilder()
    clientBuilder.addInterceptor(clientMetricsInterceptorFactory.create(name))
    for (factory in interceptorFactories) {
      val interceptor = factory.create(action) ?: continue
      clientBuilder.addNetworkInterceptor(NetworkInterceptorWrapper(action, interceptor))
    }

    val grpcClient = GrpcClient.Builder()
      .client(clientBuilder.build())
      .baseUrl(baseUrl)
      .build()

    // There should be *exactly one constructor* that takes in a grpcClient
    val delegate: G = grpcClientClass.constructors.first().call(grpcClient)

    return MethodInvocationHandler(delegate, method)
  }

  private class MethodInvocationHandler<T : Service, G : T>(
    val delegate: G,
    val method: Method
  )
}
