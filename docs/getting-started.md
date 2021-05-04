Getting Started
===============

The easiest way to get started is to copy the
[Misk exemplar project](https://github.com/cashapp/misk/tree/master/samples/exemplar). 
This exemplar contains a Misk web app with the requisite dependencies.

## Start the service

Run `ExemplarService#main`, or use gradle to run:

```bash
./gradlew run
```

## Set up bindings

Misk is built on top of the [Guice](https://github.com/google/guice) dependency injection library. 
A Misk service sets up an injector with modules that add functionality:

```kotlin
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
```

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

You can use `WebTestingModule` to set up a running web server and make `WebTestClient` available.

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
      install(WebTestingModule())
      install(HelloModule())
    }
  }
}
```
