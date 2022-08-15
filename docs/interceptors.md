# Interceptors

Misk has interceptors (middleware) to observe & potentially transform HTTP messages. The pattern
borrows from [OkHttp Interceptors].

Interceptors can be for **inbound calls** to Misk's Jetty webserver from an external client, or
**outbound calls** from Misk's OkHttpClient to an external service.

Interceptors can be **network interceptors** that operate on the encoded HTTP messages, or
**application interceptors** that operate on decoded value objects.

Misk has both **built-in** interceptors and **user-provided** interceptors. In all cases,
interceptors must be registered with a Guice multibinding.

## Inbound Network Interceptors

These run for inbound HTTP calls into Misk.

They may short-circuit the inbound calls, preventing the calls from ever reaching the destination
action. For example, the concurrency limiting (ie. load shedding) interceptor short-circuits inbound
calls when it predicts a timeout. Inbound calls may also be short-circuited if authentication
headers are absent or inadequate.

```kotlin
/** This sample interceptor decompresses the inbound request body. */
class GunzipRequestBodyInterceptor : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val httpCall = chain.httpCall
    val contentEncoding = httpCall.requestHeaders[CONTENT_ENCODING]
      ?: return chain.proceed(httpCall)
    if (contentEncoding.lowercase() == GZIP) {
      httpCall.takeRequestBody()?.let {
        httpCall.putRequestBody(GzipSource(it).buffer())
      }
    }
    chain.proceed(httpCall)
  }
}
```

Though inbound interceptors may rewrite the inbound request, they generally run too late to rewrite
the outbound response. In particular, by the time the `proceed()` call returns, the outbound HTTP
response has already been encoded and transmitted.

The built-in inbound network interceptors are:

 * GunzipRequestBodyInterceptor
 * RebalancingInterceptor
 * InternalErrorInterceptorFactory
 * RequestLogContextInterceptor
 * MetricsInterceptor
 * ConcurrencyLimitsInterceptor
 * TracingInterceptor
 * ExceptionHandlingInterceptor
 * RequestLoggingInterceptor 

Multibind these with `NetworkInterceptor.Factory`.

## Inbound Application Interceptors

These run after the network interceptors, and after the request body has been decoded into the type
specified by the target action.

```kotlin
class RequestBodyLoggingInterceptor: ApplicationInterceptor {
  override fun intercept(chain: Chain): Any {
    val result = chain.proceed(chain.args)
    log("Request arguments: ${chain.args}, response value: $result")
    return result
  }
}
```

Working in this layer is convenient because the `chain` argument has the action, its args, its
function, and its `HttpCall`. Because the action is already selected and its arguments are already
decoded, this layer cannot be used to rewrite the bytes of the request! But it can change the
arguments to the function (in a type-safe way). 

The built-in inbound application interceptors are:

 * RequestBodyLoggingInterceptor.Factory

Multibind these with `ApplicationInterceptor.Factory`.

## Outbound Application Interceptors

On inbound calls, network interceptors run before application interceptors. On outbound calls, the
ordering is reversed: application interceptors run first.

Outbound application interceptors use OkHttp's `Interceptor` type for calls. Multibind these using
`ClientApplicationInterceptorFactory`.

```kotlin
class LoggingInterceptor : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    log("Call to ${targetName(chain)} returned ${response.code}")
    return response
  }

  private fun targetName(chain: Interceptor.Chain): String? {
    val invocation = chain.request().tag(Invocation::class.java)
    if (invocation != null) return "$clientName.${invocation.method().name}"

    val grpcMethod = chain.request().tag(GrpcMethod::class.java)
    if (grpcMethod != null) return "$clientName.${grpcMethod.path.substringAfterLast("/")}"

    return null
  }
}
```

Calls made via Retrofit or the Wire gRPC client have metadata objects available as tags on the
HTTP request. You can use these to find out which Retrofit method was called (and its arguments),
or which gRPC method was called.

In these interceptors it's possible to rewrite the request body, request URL, and request headers.
It's also possible to rewrite the returned response body, headers, or status code. You might do this
to inject a missing response header or remove one.

The built-in outbound application interceptors are:

 * ClientMetricsInterceptor

Multibind these with `ClientApplicationInterceptorFactory`.

## Outbound Network Interceptors

These run on outbound calls after the outbound application interceptors. These run after the socket
connection to the remote HTTP server has been established.

```kotlin
class LoggingInterceptor : ClientNetworkInterceptor {
  override fun intercept(chain: ClientNetworkChain): Response {
    log("executing ${chain.action}")
    return chain.proceed(chain.request)
  }
}
```

Network interceptors cannot rewrite the destination hostname or URL. They can rewrite outbound
request bodies and request headers. They can also rewrite returned status codes, bodies and headers.

Misk has no built-in network interceptors.

Multibind these with `ClientNetworkInterceptor.Factory`.


[OkHttp Interceptors]: https://square.github.io/okhttp/features/interceptors/
