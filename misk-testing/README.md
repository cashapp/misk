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
