This module provides access to Google Cloud SDKs.

## Spanner module

The `GoogleSpannerModule` provides a `Spanner` client from the GCP SDKs that
you can inject into your own modules. This client can be used to make calls to
a Spanner database. Additionally, the module provides the Spanner emulator that
runs as a Docker container for you to develop and test your application with.
It automatically starts when the module is installed.

### Configuring the Spanner module

This is an example YAML configuration for a typical Spanner project:

```yaml
gcp_spanner:
  project_id: test-project
  instance_id: test-instance
  database: test-database
  emulator:
    enabled: true
    host: localhost
    port: 9010
```

You _must_ provide, at a minimum, the `project_id`, `instance_id`, and
`database`. These are all visible in the Spanner part of the GCP Console. Then,
import that configuration into your service's configuration module, which will
automatically populate from the YAML file:

```kotlin
/** Configuration for MyService. */
data class MyServiceConfig(
  /**
   * Configuration for Spanner database.
   */
  val gcp_spanner: SpannerConfig,
) : Config
```

Finally, you can add the `GoogleSpannerModule` to your list of application
modules, as shown below.

> Make sure that the `DeploymentModule` is loaded before `GoogleSpannerModule`,
> as the deployment environment is required to load the Spanner module.

Example of loading the `GoogleSpannerModule` in `MyService`:

```kotlin
fun applicationModules(
  serviceBuilder: ServiceBuilder<MyServiceConfig>
): List<KAbstractModule> {
  return listOf(
    ConfigModule.create(serviceBuilder.name, serviceBuilder.config),
    DeploymentModule(serviceBuilder.deployment),
    GoogleSpannerModule(serviceBuilder.config.gcp_spanner),
  )
}
```

### Using the Spanner module

In any module / action that supports injection, inject a Spanner instance and
it will automatically be setup with the settings you configured (one instance
is reused throughout the life of your service).

For example, to use Spanner to read some data in an action:

```kotlin
@Singleton
class HelloWebAction @Inject constructor(
  val config: MyServiceConfig,
  val spanner: Spanner, // Inject the Spanner client
) : WebAction {
  @Get("/person")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun person(): PersonResponse {
    // Initialize a sub-client for doing read transactions 
    val dataClient = spanner.getDatabaseClient(
      DatabaseId.of(
        config.gcp_spanner.project_id,
        config.gcp_spanner.instance_id,
        config.gcp_spanner.database,
      )
    )

    // Read a person
    dataClient.readOnlyTransaction()
      .read(
        "people",
        KeySet.singleKey(Key.of(personId)),
        listOf("id")
      )

    // Load results
    if (query.next()) {
      val personId = query.getString(0)
      return PersonResponse(personId) 
    } else {
      throw NotFoundException()
    }
  }
}

data class PersonResponse(val id: String)
```

> Tip: You may want to build a Spanner module for your service to inject
> sub-clients for Spanner - specifically for the `DatabaseClient` and
> `InstanceAdminClient` classes - since they're what you'll likely be calling
> in your business logic.

### Testing with the Spanner module

Like loading the module for your main application runtime, you'll want to add
the `GoogleSpannerModule` to your service's testing module:

```kotlin
class MyServiceTestingModule : KAbstractModule() {
  override fun configure() {
    val config: MyServiceConfig = SkimConfig.load(
      appName = SERVICE_NAME,
      deployment = TESTING,
      configDirs = arrayOf(),
    )
    install(ConfigModule.create(SERVICE_NAME, config))
    install(MiskTestingServiceModule())
    install(GoogleSpannerModule(config.gcp_spanner))
  }
}
```

Then, follow the [Misk testing guide](https://cashapp.github.io/misk/getting-started/#test-the-endpoint)
to add the `@MiskTest` and `@MiskTestModule` annotations, which will ensure
your test has the Spanner client injected if you need it for any reason.

#### Cleaning up your database between tests

In some cases, you need to clean up data in your database after doing some 
writes. The Spanner emulator supports this with the `clearTables()` method.
Inject the `emulator` into your test and call `clearTables` to delete every
row from each table in your Spanner emulator:

```kotlin
@MiskTest()
class GoogleSpannerModuleTest {
  @MiskTestModule val module = MyServiceTestingModule()
  @Inject lateinit var emulator: GoogleSpannerEmulator

  @BeforeEach fun cleanDb() {
    emulator.clearTables()
  }
}
```