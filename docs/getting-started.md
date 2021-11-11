Getting Started
===============

Misk is an application container for Kotlin. It provides libraries for common application concerns
like serving endpoints, caching, queueing, persistence, distributed leasing, and clustering.

The easiest way to get started is to copy the 
[Misk exemplar project](https://github.com/cashapp/misk/tree/master/samples/exemplar). This exemplar 
contains a Misk web app with the requisite dependencies.

## Start the service

Run `ExemplarService#main`, or use gradle to run:

```bash
./gradlew run
```

## Set up bindings

A Misk application is wired together using [Guice](https://github.com/google/guice). Features of
Misk are managed by [Guava `Services`](https://github.com/google/guava/wiki/ServiceExplained),
provided by [Guice `Modules`](https://github.com/google/guice/wiki/GettingStarted), and configured
using Misk `Config`s. For example, if your application needs a Redis cache, you would install
[`RedisModule`](https://github.com/cashapp/misk/blob/master/misk-redis/src/main/kotlin/misk/redis/RedisModule.kt),
and add a corresponding `RedisConfig` to your application’s config YAML.

Misk is unopinionated about which of its features your application chooses to use, and offers
multiple alternatives for some common concerns.

### The main function
The entry point to every Misk application is
[`MiskApplication`](https://github.com/cashapp/misk/blob/master/misk/src/main/kotlin/misk/MiskApplication.kt):

```kotlin
fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val env = Env(environment.name)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", env)

  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    ExemplarAccessModule(),
    ExemplarWebActionsModule(),

    // e.g. to add an admin dashboard:
    AdminDashboardModule(isDevelopment = true)
  ).run(args)
}
```

## Set up configuration

Every Misk application has a top-level class that implements the `Config` marker interface. This
`Config` encapsulates all of the configuration for the app.

By default, configs are loaded from YAML files at the app’s resources root. Each value in the config
must have a corresponding entry in the YAML file.

Modules in Misk that require configuration usually have their own `Config` objects. If you want to
use the Module in your app, you should add them as properties of your app’s `Config` object, for
example:

```kotlin
data class MyAppConfig(
    val my_config_value: String,
    val http_clients: HttpClientsConfig
) : Config
```

This then corresponds to a YAML file:

```yaml
my_config_value: "this value"

http_clients:
  # ... config
```

### Config resolution
Configs are loaded using the app’s resource loader. The config loader looks for files in the
following order by default:

1. `$SERVICE_NAME-common.yaml`
2. `$SERVICE_NAME-$ENVIRONMENT.yaml`

At least one of `$SERVICE_NAME-common.yaml` or `$SERVICE_NAME-$ENVIRONMENT.yaml` must exist. 

Values from later files take precedence.

## Write an endpoint

Actions are Misk's unit for an endpoint.

Actions inherit from `WebAction` and have a `@Get`/`@Post` annotation:

```kotlin
@Singleton
class HelloWebAction @Inject constructor() : WebAction {
  @Get("/hello/{name}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun hello(
    @PathParam name: String,
  ): HelloResponse {
    return HelloResponse(name)
  }
}

data class HelloResponse(val name: String)
```

Read more about this in [Actions](./actions.md).

## Test the endpoint

You can unit test directly:
```kotlin
class HelloWebActionTest {
  @Test
  fun `tests the unit`() {
    assertThat(HelloWebAction().hello("sandy", headersOf(), null, null))
        .isEqualTo(HelloResponse("sandy"))
  }
}
```

Integration tests set up a module for you, and adds an injector to the test class.

You can use `WebServerTestingModule` to set up a running web server and make `WebTestClient` 
available.

```kotlin
@MiskTest(startService = true)
class HelloWebActionTest {
  @MiskTestModule val module = TestModule()

  @Inject private lateinit var webTestClient: WebTestClient

  @Test
  fun `tests a request being made`() {
    val hello = webTestClient.get("/hello/sandy")
    assertThat(hello.response.code).isEqualTo(200)
    assertThat(hello.parseJson<HelloResponse>())
      .isEqualTo(HelloResponse("sandy"))
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(HelloModule())
    }
  }
}
```

Read more about this in [Actions](actions.md#testing)

## Create services

The main function is just an entry point for wiring together components of Misk. Long-running
threads that do the real work are written as `Services` using [Guava’s Service
Framework](https://github.com/google/guava/wiki/ServiceExplained).

A `Service` is bound by installing a `ServiceModule`, for example:
```kotlin
class MyServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<MyService>())
  }
}
```

Notice that in this examplewe use
[`KAbstractModule()`](https://github.com/square/misk/blob/master/misk/src/main/kotlin/misk/inject/KAbstractModule.kt),
Misk’s Kotlin wrapper for
[`AbstractModule`](https://google.github.io/guice/api-docs/latest/javadoc/index.html?com/google/inject/AbstractModule.html),
as our base Module class.

`MiskApplication` will start all services installed by a `ServiceModule`.

If there is a `Service` that must be run after a other set of `Services` have started, the
service dependency graph should be specified at the installation site.

For example, if you are operating a movie service, which needs a database:
```kotlin
class MovieServiceModule : KAbstractModule() {
  override fun configure() {
    // Note that DatabaseService does not have to be installed here.
    // It could be installed in another KAbstractModule if preferred.
    install(ServiceModule<DatabaseService>())

    // Multiple dependencies can be added by chaining calls to `dependsOn`.
    install(ServiceModule<MovieService>()
        .dependsOn<DatabaseService>())
  }
}
```
See [`ServiceModule`](0.x/misk-service/misk/-service-module/index.md) for more details about the 
service graph.

When writing `Services`, always prefer to inherit from one of the common base classes:
[`AbstractIdleService`](https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/AbstractIdleService.html),
[`AbstractScheduledService`](https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/AbstractScheduledService.html),
or
[`AbstractExecutionThreadService`](https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/AbstractExecutionThreadService.html).
See [Services Explained](https://github.com/google/guava/wiki/ServiceExplained) for details. If your
service is can make use of exponential backoff and scheduling, take a look at using
[`RepeatedTaskQueue`](https://github.com/cashapp/misk/blob/master/misk/src/main/kotlin/misk/tasks/RepeatedTaskQueue.kt).
