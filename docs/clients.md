# Clients

Misk provides configurable HTTP clients built on top of [OkHttp](https://github.com/square/okhttp)
and [Retrofit](https://github.com/square/retrofit), and gRPC clients built on top of
[Wire](https://github.com/square/wire).

## HTTP Clients

### Config

Set up a Config object (and use `MiskConfig.load` to load config from a YAML file):

```kotlin
data class MyServiceConfig(
  val http_clients: HttpClientsConfig,
  // ...
) : Config
```

In the configuration YAML, specify the target service's address:

```yaml
http_clients:
  ...
  endpoints:
    ...
    greeter:  { url: "https://hello.example.com" }
```

### Create an OkHttpClient

Use `HttpClientModule`:

```kotlin
class MyClientModule : KAbstractModule() {
  override fun configure() {
    install(HttpClientModule(
      // Corresponds to the YAML config. Requires a bound HttpClientsConfig
      name = "greeter",
      // Optional annotation to define how you inject your client dependency
      annotation = Names.named("greeterHttp")
    ))
  }
}
```

This binds an `OkHttpClient` that you can inject:

```kotlin
class MyClient @Inject constructor(@Named("greeterHttp") val client: OkHttpClient) {
  fun callGreeter() {
    val response = client.newCall(
      Request.Builder()
        .url("http://localhost:8080/hello")
        .build()
    ).execute()
  }
}
```

### Create typed clients with Retrofit

First, create a Retrofit interface. See [the Retrofit docs](https://square.github.io/retrofit/) for
more details.

```kotlin
interface GreeterApi {
  @POST("/hello")
  @Headers(value = ["accept: application/json"])
  fun hello(
    @Body request: HelloRequest
  ): Call<HelloResponse>
}
```

Next, install a `TypedHttpClientModule` with this interface.

```kotlin
class HelloClientModule : KAbstractModule() {
  override fun configure() {
    install(
      TypedHttpClientModule(
        GreeterApi::class, 
        // Corresponds to the YAML config. Requires a bound HttpClientsConfig
        name = "greeter", 
        // Optional annotation to define how you inject your client dependency
        annotation = Names.named("greeterApi")
      )
    )
  }
}
```

Now you can inject an implementation of this client:

```kotlin
@Singleton class MyApiClient @Inject constructor(
  @Named("greeterApi") private val api: GreeterApi
) {
  fun hello(message: String): String {
    val response = api.hello(
      HelloRequest(
        message = message
      )
    ).execute()
  }
}
```

## gRPC Clients

First, include the auto-generated gRPC client code at the caller module using the Wire Gradle 
plugin.

```kt
plugins {
  id("com.squareup.wire")
}

wire {
  sourcePath {
    srcDir("src/main/proto")
  }

  // Generate Kotlin for the gRPC client API.
  kotlin {
    // Set this to false if you're generating client and server interfaces in one module
    exclusive = false
    includes ("squareup.cash.hello.GreeterService")
    rpcRole = "client"
  }

  java {
  }
}
```

Next, bind your client in code in a similar fashion to an HTTP client. Set up client configuration,
as described in [Config](#config). Then, bind a `GrpcClientModule`:

```kotlin
class GreeterClientModule : KAbstractModule() {
  override fun configure() {
    install(GrpcClientModule.create<GreeterServiceClient, GrpcGreeterServiceClient>(
        // Corresponds to the YAML config. Requires a bound HttpClientsConfig
        name = "greeter",
        // Optional annotation to define how you inject your client dependency
        annotation = Names.named("greeterGrpc")
    ))
  }
}
```

With this all setup, you can now inject your client in source code and connect via gRPC:

```kotlin
internal class GrpcGreeterServiceClient @Inject internal constructor(
  @Named("greeterGrpc") private val greeterGrpc: GreeterServiceClient
) {
  fun get(message: String) {
    val response = greeterGrpc.Hello().executeBlocking(HelloRequest(message))

    // ... do something with the response here
  }
}
```
