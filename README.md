<img src="https://github.com/cashapp/misk/raw/master/misk.png" width="300">


* Releases
  * See most recent [public build][snap]
  * [change log][changelog]
  * API is subject to change

* Documentation
  * [Project Website][misk]
  * [Getting Started](./docs/getting-started.md)
  * [User's Guide](./docs/user-guide.md)
  * [Developer's Guide](./docs/develops-guide.md)

* Related Projects
  * [misk-web][miskweb]
  * [wisp][wisp]: wisp now is part of Misk
 

# Overview
Misk (Microservice Container in Kotlin) is an open source microservice container from Cash App.
It allows you to quickly create a microservice in Kotlin or Java, and provides libraries for common
concerns like serving endpoints, caching, queueing, persistence, distributed leasing and clustering.
It also includes [the Wisp library](./wisp/README.md), which is a collection of Kotlin modules
providing various features and utilities, including config, logging, feature flags and more.

## Modules

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

## wisp-*

The wisp-* modules contain no Dependency Injection based code (i.e. no Guice, etc) and back many misk-* module implementations.  

## Looking for Misk-Web?

[Misk-Web][miskweb] powers the Misk Admin Dashboard with modular Typescript + React powered tabs.

[changelog]: http://cashapp.github.io/misk/changelog/
[misk]: https://cashapp.github.io/misk/
[miskweb]: https://cashapp.github.io/misk-web/
[snap]: https://mvnrepository.com/artifact/com.squareup.misk/misk
[wisp]: https://github.com/cashapp/wisp
