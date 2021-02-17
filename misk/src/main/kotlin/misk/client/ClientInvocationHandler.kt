package misk.client

import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import io.opentracing.contrib.okhttp3.TracingCallFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberFunctions
import misk.web.mediatype.MediaTypes
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.protobuf.ProtoConverterFactory
import retrofit2.converter.wire.WireConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.HTTP
import retrofit2.http.Headers
import retrofit2.http.OPTIONS
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

internal class ClientInvocationHandler(
  private val interfaceType: KClass<*>,
  private val clientName: String,
  retrofit: Retrofit,
  okHttpTemplate: OkHttpClient,
  networkInterceptorFactories: Provider<List<ClientNetworkInterceptor.Factory>>,
  eventListenerFactory: EventListener.Factory?,
  tracer: Tracer?,
  moshi: Moshi,
  clientMetricsInterceptorFactory: ClientMetricsInterceptor.Factory
) : InvocationHandler {

  private val actionsByMethod = interfaceType.functions
      .filter { it.isRetrofitMethod }
      .map { ClientAction(clientName, it) }
      .map { it.function.name to it }
      .toMap()

  // Each method might have a different set of network interceptors, so sadly we potentially
  // need to create a separate OkHttpClient and retrofit proxy per method
  private val proxiesByMethod: Map<String, Any> = actionsByMethod.map { (methodName, action) ->
    val networkInterceptors = networkInterceptorFactories.get().mapNotNull { it.create(action) }
    val clientBuilder = okHttpTemplate.newBuilder()
    clientBuilder.addInterceptor(clientMetricsInterceptorFactory.create(clientName))
    networkInterceptors.forEach {
      clientBuilder.addNetworkInterceptor(NetworkInterceptorWrapper(action, it))
    }
    if (eventListenerFactory != null) {
      clientBuilder.eventListenerFactory(eventListenerFactory)
    }
    val actionSpecificClient = clientBuilder.build()

    val retrofitBuilder = retrofit.newBuilder()
        .client(actionSpecificClient)

    if (tracer != null) retrofitBuilder.callFactory(
        TracingCallFactory(actionSpecificClient, tracer))

    val mediaTypes = getEndpointMediaTypes(methodName)
    if (mediaTypes.contains(MediaTypes.APPLICATION_PROTOBUF)) {
      retrofitBuilder.addConverterFactory(WireConverterFactory.create())
      retrofitBuilder.addConverterFactory(ProtoConverterFactory.create())
    }
    // Always add JSON as default. Ensure it's added last so that other converters get a chance since
    // JSON converter will accept any object.
    retrofitBuilder.addConverterFactory(MoshiConverterFactory.create(moshi))

    methodName to retrofitBuilder
        .build()
        .create(interfaceType.java)
  }.toMap()

  init {
    require(actionsByMethod.isNotEmpty()) {
      "$interfaceType is not a Retrofit interface (no @POST or @GET methods)"
    }
  }

  private fun getEndpointMediaTypes(methodName: String): List<String> {
    val headers =
        interfaceType.memberFunctions.find { it.name == methodName }?.findAnnotation<Headers>()
            ?: return emptyList()

    return headers.value.mapNotNull {
      val (headerKey, headerValue) = it.split(":").map { it.trim() }
      if (headerKey.equals("Accept", true) || headerKey.equals("Content-type", true)) {
        headerValue.toLowerCase()
      } else {
        null
      }
    }.filterNot { it == MediaTypes.APPLICATION_JSON }
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
    val argsList = args?.toList() ?: listOf()

    val retrofitProxy = proxiesByMethod[method.name] ?: throw IllegalStateException(
        "no action corresponding to ${interfaceType.qualifiedName}#${method.name}"
    )

    return method.invoke(retrofitProxy, *argsList.toTypedArray()) as Call<Any>
  }
}

internal class NetworkInterceptorWrapper(
  val action: ClientAction,
  val interceptor: ClientNetworkInterceptor
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
    return interceptor.intercept(RealClientNetworkChain(chain, action))
  }
}

private val KFunction<*>.isRetrofitMethod: Boolean
  get() {
    return annotations.any {
      it is GET || it is POST || it is HEAD || it is PUT || it is PATCH || it is OPTIONS ||
          it is DELETE || it is HTTP
    }
  }
