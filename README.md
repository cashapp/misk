<img src="https://github.com/cashapp/misk/raw/master/misk.png" width="300">

See the [project website][misk] for documentation and APIs.

Misk is a new open source application container from Cash App.

Misk is not ready for use. The API is not stable.

# Releases

Our [change log][changelog] has release history. API is subject to change. 

```kotlin
implementation("com.squareup.misk:misk:0.16.0")
```

Snapshot builds are [available][snap].


# Modules

### misk-actions

The core annotations and interfaces necessary to define actions that can be hosted in Misk.
This package has no dependency on the enclosing container (Misk!) and so your actions can be
used in other environments without any heavy dependencies.

Actions should extend `WebAction`, be annotated with a HTTP method like `@Post`, accept a
request object and return a response object. Throw an exception like `BadRequestException` to
fail the request without much boilerplate.


### misk-service
 
Bind Guava services with inter-service dependencies.
 
Any service can depend on any other service. ServiceManager won't start a service until the
services it depends on are running.


### misk-inject
 
Integrates Guice with Kotlin.

Extending `KAbstractModule` instead of Guice's `AbstractModule` lets you use `KClass` instead
of `java.lang.Class` and other conveniences.


### misk-feature
 
Runtime feature flags. `misk-launchdarkly` is the reference implementation.


### misk-jobqueue
 
A job queue with a high quality fake. `AwsSqsJobQueueModule` from `misk-aws` is the reference 
implementation.


### misk-events
 
An event publisher + consumer. There is no open source reference implementation at this time.


## Looking for Misk-Web?

Misk-Web powers the Misk Admin Dashboard with modular Typescript + React powered tabs. 


**Check out [Misk-Web][miskweb]!**

[changelog]: http://cashapp.github.io/misk/changelog/
[misk]: https://cashapp.github.io/misk/
[miskweb]: https://cashapp.github.io/misk-web/
[snap]: https://oss.sonatype.org/content/repositories/snapshots/
