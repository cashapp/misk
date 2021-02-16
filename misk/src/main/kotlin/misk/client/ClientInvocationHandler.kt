package misk.client

import com.google.common.util.concurrent.SettableFuture
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
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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
  applicationInterceptorFactories: Provider<List<ClientApplicationInterceptor.Factory>>,
  eventListenerFactory: EventListener.Factory?,
  tracer: Tracer?,
  moshi: Moshi,
  clientMetricsInterceptorFactory: ClientMetricsInterceptor.Factory
) : InvocationHandler {

  private val actionsByMethod = interfaceType.functions
      .mapNotNull { it as? KFunction<*> }
      .filter { it.isRetrofitMethod }
      .map { ClientAction(clientName, it) }
      .map { it.function.name to it }
      .toMap()

  private val interceptorsByMethod = actionsByMethod.map { (methodName, action) ->
    methodName to applicationInterceptorFactories.get().mapNotNull { it.create(action) }
  }.toMap()

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

    val action = actionsByMethod[method.name] ?: throw IllegalStateException(
        "no action corresponding to ${interfaceType.qualifiedName}#${method.name}"
    )

    val retrofitProxy = proxiesByMethod[method.name] ?: throw IllegalStateException(
        "no action corresponding to ${interfaceType.qualifiedName}#${method.name}"
    )
    val interceptors = (interceptorsByMethod[method.name] ?: listOf())

    val beginCallInterceptors = interceptors + RetrofitCallInterceptor(retrofitProxy, method)
    val beginCallChain = RealBeginClientCallChain(action, argsList, beginCallInterceptors)
    val wrappedCall = beginCallChain.proceed(beginCallChain.args)

    val requestInterceptors = interceptors + RetrofitRequestInterceptor(wrappedCall)
    return InterceptedCall(action, requestInterceptors, argsList, wrappedCall)
  }

  /** Wraps a retrofit [Call] to invoke interceptors before handing off to Retrofit */
  private class InterceptedCall(
    private val action: ClientAction,
    private val interceptors: List<ClientApplicationInterceptor>,
    private val args: List<*>,
    private val wrapped: Call<Any>
  ) : Call<Any> {
    override fun enqueue(callback: Callback<Any>) {
      val allInterceptors = interceptors + RetrofitRequestInterceptor(wrapped)
      RealClientChain(action, args, wrapped, callback, allInterceptors).proceed(args, callback)
    }

    override fun execute(): Response<Any> {
      val future = SettableFuture.create<Response<Any>>()
      enqueue(SyncCallback(future))
      return future.get()
    }

    override fun isCanceled() = wrapped.isCanceled
    override fun isExecuted() = wrapped.isExecuted
    override fun clone() = InterceptedCall(action, interceptors, args, wrapped.clone())
    override fun cancel() = wrapped.cancel()
    override fun request(): Request = wrapped.request()
    override fun timeout(): Timeout = wrapped.timeout()
  }

  /** Interceptor that builds the call through Retrofit */
  private class RetrofitCallInterceptor(
    private val retrofitProxy: Any,
    private val method: Method
  ) : ClientApplicationInterceptor {
    override fun interceptBeginCall(chain: BeginClientCallChain): Call<Any> {
      @Suppress("UNCHECKED_CAST")
      return method.invoke(retrofitProxy, *chain.args.toTypedArray()) as Call<Any>
    }

    override fun intercept(chain: ClientChain) {
      throw IllegalStateException("RetrofitCallInterceptor should never be called during intercept")
    }
  }

  /** Interceptor that hands the request off to Retrofit */
  private class RetrofitRequestInterceptor(val call: Call<Any>) : ClientApplicationInterceptor {
    override fun interceptBeginCall(chain: BeginClientCallChain): Call<Any> {
      throw IllegalStateException(
          "RetrofitRequestInterceptor should never be called during interceptBeginCall"
      )
    }

    override fun intercept(chain: ClientChain) = call.enqueue(chain.callback)
  }

  /** Retrofit callback that triggers a synchronous future on completion */
  private class SyncCallback<T>(private val future: SettableFuture<Response<T>>) : Callback<T> {
    override fun onFailure(call: Call<T>, t: Throwable) {
      future.setException(t)
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
      future.set(response)
    }
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
