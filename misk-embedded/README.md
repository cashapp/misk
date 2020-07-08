Misk Embedded
=============

Sometimes we want to embed a Misk Service in another JVM project (such as another Misk service,
but not necessarily) while avoiding classpath collisions on dependencies. For example, some services
may use Hibernate 4 and want to embed Misk applications that use Hibernate 5.

Misk embedded encapsulates a Misk service and its classpath for use in another service.


Requirements
------------

The embedded project should have a client/ and service/ submodule: 
 * The service/ submodule should depend on the client/ submodule
 * The client/ submodule MUST NOT depend on the service/ submodule.

The consuming project depends on the embedded project's client/ submodule.


Usage
-----

1. Create an embedding interface in your service's client/ submodule.  

2. Implement that embedding interface in your service's service/ submodule.  

3. Implement an `InjectorFactory` in your service's service/ submodule. The InjectorFactory class
   MUST be named the same as the embedding interface, with the suffix "InjectorFactory". For
   example, `EmbeddedSampleServiceInjectorFactory`. This injector needs to bind the embedding
   interface to its implementation in the service submodule.

4. Copy the misk-embedded-sample/embedded/ directory into your project as a new embedded/
   submodule. Edit `build.gradle` and `gradle.properties` to use your project's embedding interface
   name.
   
5. In each consuming project, do this:

    ```
    val myService = EmbeddedMisk.create<MyService>()
    ```

   
Warning!
--------

The embedded service will be loaded in an isolated class loader. This means it won't collide with
classes in the consuming service, even if they share the same name. Static singletons will not be
singleton because each embedded service gets its own copies.


How It Works
------------

We create an isolated class loader that decides which classes get private copies. The rules are:

 * Any class depended upon by the client is NOT in the isolated class loader. This includes the
   embedding interface, its transitive dependencies, Guava, Guice, etc.
   
 * Any other class depended upon by the service is in the isolated class loader. Typically this
   is the service implementation details and their dependencies.

The `embedded` submodule builds a JAR with just the isolated classes, and the `MiskEmbedded` class
creates the isolated class loader to load those classes.

The embedding interface is the bridge from the main application code into the isolated class loader.
Its defined in shared code, but implemented in isolated code.  
