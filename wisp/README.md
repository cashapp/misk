<img src="https://github.com/cashapp/misk/raw/master/misk.png" width="300">

See the [project website][misk] for documentation and APIs.

Misk is a new open source application container from Cash App.

Misk is not ready for use. The API is not stable.

# Releases

Our [change log][changelog] has release history. API is subject to change. 

```kotlin
implementation("com.squareup.misk:misk:0.24.0")
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


### misk-aws

Integrate with Amazon Web Services, and includes packages to integrate with S3 and SQS.


### misk-aws-dynamodb

Integrate with AWS DynamoDb using AWS SDK for Java 1.x. It should be safe to install side-by-side
with `misk-aws2-dynamodb` if you need to use features in both.


### misk-aws-dynamodb-testing

Integrate with this package to write tests for code that interacts with DynamoDb.
Exposes APIs via AWS SDK for Java 1.x. Use alongside with `misk-aws-dynamodb`.

Installing `InProcessDynamoDbModule` runs a DynamoDb Local instance in memory for your
tests to run against. This module is recommended over `DockerDynamoDbModule` because there is less
overhead in test execution performance.

Installing `DockerDynamoDbModule` runs a DynamoDB Local instance in Docker for your tests to execute
against.


### misk-aws2-dynamodb

Integrate with AWS DynamoDb using AWS SDK for Java 2.x. It should be safe to install side-by-side
with `misk-aws-dynamodb` if you need to use features in both.

Please read
the [AWS SDK for Java 2.x Migration Guide](https://docs.aws.amazon.com/sdk-for-java/latest/migration-guide/what-is-java-migration.html)
for more details.


### misk-aws2-dynamodb-testing

Integrate with this package to write tests for code that interacts with DynamoDb.
Exposes APIs via AWS SDK for Java 2.x. Use alongside with `misk-aws2-dynamodb`.

Installing `InProcessDynamoDbModule` runs a DynamoDb Local instance in memory for your
tests to run against. This module is recommended over `DockerDynamoDbModule` because there is less
overhead in test execution performance.

Installing `DockerDynamoDbModule` runs a DynamoDB Local instance in Docker for your tests to execute
against.


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

## What are the wisp* modules?

The wisp* modules contain no Dependency Injection based code (i.e. no Guice, etc).  These modules 
should never refer to misk* modules, although misk* modules can (and should) use wisp* modules.

Also, modules that are wisp*-testing will only be used in test scope in other wisp modules, never 
in the api/implementation scope. 

If you are refactoring code from misk into the wisp modules, you must not break any external Misk dependencies
or apis.  It is ok to deprecate items in misk to encourage eventual migration to wisp directly if desired. If
your refactoring does not fit one of the existing wisp modules, create a new module.  For now, it is preferred
to have many small modules rather than larger conglomerate modules requiring many different dependencies.

It should be considered that wisp will be volatile for sometime with the potential for a lot of changes, additions, etc.
Misk apps should use wisp modules directly with caution as breaking changes might be required.
