# misk-testing

Tests annotated with `@MiskTest` are misk tests. Misk tests automate injector setup and Guava
`Service` lifecycle.

A misk test allows the annotated class to have a field of type `Module` annotated
`@MiskTestModule`. This module will be used to create the injector and inject any other fields.

Additionally, Misks integrates with
Guice [BoundFieldModule](https://github.com/google/guice/wiki/BoundFields#binding-fields-using-boundfieldmodule)
to declaratively bind fields defined in misk tests to complement `@MiskTestModule` modules.
Each `@Bind` annotated field will bind the field's type to value at injector creation time.

Guava `Service` lifecycle is opt-in; do this by specifying `@MiskTest(startService = true)`.

Note that injection and `Service` start/stop occur for each test method. This provides a fresh setup
for each test method for state changed by injection/services.

Misk tests are also compatible with JUnit's `@Nested` classes. Any `@Nested` classes inherit all the
misk test setup from their outermost class. This means that only the outermost test class can have
`@MiskTest`, `@MiskTestModule`, `@MiskExternalDependency` annotations. They are ignored on `@Nested`
classes. Additionally, the injector only injects members into the outermost class.

You can check [code samples here](./src/test/kotlin/misk/testing).

## Speeding up Tests by Reusing the Injector instance and Service Lifecycle

> [!WARNING]
> This is a new feature and marked as experimental. The APIs are likely to change.

By default, Misk creates a new injector and starts/stops `Service`s for each test method. This can be slow depending on the size of dependency graph and the type of services involved.

To speed up tests, you can reuse the injector instance and service lifecycle across tests by:
1. Extending `misk.inject.ReusableTestModule` in your `@MiskTestModule` annotated modules.
2. Setting the `MISK_TEST_REUSE_INJECTOR` environment variable to true for the test task in your build file.
```kotlin
tasks.withType<Test>().configureEach {
  environment("MISK_TEST_REUSE_INJECTOR", "true")
}
```

The environment variable can be set to false in order to opt your tests out of this feature entirely.

Reusing the injector across tests means that the stateful dependencies such as DBs and fake objects can have their state updated during the execution of each test, which can impact the result of the following tests. Reset the state of such dependencies by:
1. Extending the `misk.testing.TestFixture` interface.
2. `multibind`ing the test fixture class in a Guice module to ensure that the test infrastructure can reset them between test runs. For example:
```kotlin
  bind<Clock>().to<FakeClock>()
  + multibind<TestFixture>().to<FakeClock>()
```
