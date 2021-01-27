# misk-testing

Tests annotated with `@MiskTest` are misk tests. Misk tests automate injector setup and Guava 
`Service` lifecycle.

A misk test requires the annotated class to have a field of type `Module` annotated 
`@MiskTestModule`. This module will be used to create the injector and inject any other fields.

Guava `Service` lifecycle is opt-in; do this by specifying `@MiskTest(startService = true)`.

Note that injection and `Service` start/stop occur for each test method. This provides a fresh setup 
for each test method for state changed by injection/services. 

Misk tests are also compatible with JUnit's `@Nested` classes. Any `@Nested` classes inherit all the
misk test setup from their outermost class. This means that only the outermost test class can have 
`@MiskTest`, `@MiskTestModule`, `@MiskExternalDependency` annotations. They are ignored on `@Nested` 
classes. Additionally, the injector only injects members into the outermost class. Any injected 
fields of inner classes will also be ignored.  

