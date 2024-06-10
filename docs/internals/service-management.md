# Misk Services

Services in Misk can depend on other services. We need to reconcile these dependencies to ensure an orderly application
startup and [shutdown](graceful-shutdown.md).

In the Misk framework, a service represents a component that starts and stops in coordination with the application's lifecycle. 
Services in Misk are typically used to manage tasks such as database connections, HTTP servers, or other long-running processes. 
They ensure that resources are correctly initialized when the application starts and properly released when it stops.
Misk uses Google's Guava [ServiceManager](https://guava.dev/releases/19.0/api/docs/com/google/common/util/concurrent/ServiceManager.html) 
to handle these services, allowing for graceful startup, shutdown, and dependency management between different services.

## Dependencies

Suppose we have a `DatabaseService` and a `MovieService`, with the `MovieService` depending on
the `DatabaseService`.

 ```
 DatabaseService
   depended on by MovieService
 ```

When you install a service via this module, start-up and shut-down of its dependencies are
handled automatically, so that a service can only run when the services it depends on are
running. In the example above, the `MovieService` doesn't enter the `STARTING` state until the
`DatabaseService` has entered the `RUNNING` state. Conversely, the `MovieService` must enter the
`TERMINATED` state before the DatabaseService enters the `STOPPING` state.

Dependencies can have their own dependencies, so there's an entire graph to manage of what starts
and stops when.

## Enhancements

Some services exist to enhance the behavior of another service.

For example, a `DatabaseService` may manage a generic connection to a MySQL database, and the
`SchemaMigrationService` may create tables specific to the application.

We treat such enhancements as implementation details of the enhanced service: they depend on the
service, but downstream dependencies like the `MovieService` don't need to know that they exist.

 ```
 DatabaseService
   enhanced by SchemaMigrationService
   depended on by MovieService
 ```

In the above service graph we start the `DatabaseService` first, the `SchemaMigrationService`
second, and finally the `MovieService`. The `MovieService` doesn't need to express a dependency
on the `SchemaMigrationService`, that happens automatically for enhancements.

## The ReadyService
This is a symbolic service that's useful to define the relationship, generally, between services which process traffic (i.e. Jetty) 
and infrastructure services (i.e. persistence stores).

By having the former depend on ReadyService and the latter enhanced by ReadyService we can force, for example, 
JettyService to stop before our feature flag service without having to intertwine our dependency graph.

In general, infrastructure services should enhance ReadyService and application services should depend on ReadyService.

See more information here [here](../0.x/misk-service/misk-service/misk/-ready-service/index.md).

## What does this look like?

### Configuration

Instead of using the regular service multi-bindings you might be used to, in the `configure`
block of a Guice [KAbstractModule], you would set up the above relationship as follows:

 ```kotlin
 override fun configure() {
   install(ServiceModule<SchemaMigrationService())
   install(
     ServiceModule<DatabaseService>()
       .enhancedBy<SchemaMigrationService>()
   )
   install(
     ServiceModule<MoviesService>()
       .dependsOn<DatabaseService>()
   )
 }
 ```

### How does this work?

Bindings are hooked up for a `ServiceManager` provider, which decorates the service with its
dependencies and enhancements to defer its start up and shut down until its dependent services
are ready.

This service will stall in the `STARTING` state until all upstream services are `RUNNING`.
Symmetrically it stalls in the `STOPPING` state until all dependent services are `TERMINATED`.

## Notes
* This doc was lifted from the doc string on the `ServiceModule` class
