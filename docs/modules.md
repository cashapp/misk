Misk Modules
===============
Misk is split into many Gradle subprojects to organize functionality and create smaller dependencies for downstream users.
Integrations with external libraries (like DynamoDB, Hibernate, etc.) should each live in their own module. 

Misk uses the [Gradle test fixtures plugin](https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures) to colocate production code with any relevant test helper classes.
However, there are some `*-testing` modules that haven't yet been migrated to test fixtures.


## Descriptions

### misk

Most of the implementation of Misk's web server components.

This is the original monolithic module. Pieces are being extracted into new modules
to align with the smaller module strategy.


### misk-actions

The core annotations and interfaces necessary to define actions that can be hosted in Misk.
This package has no dependency on the enclosing container (Misk!) and so your actions can be
used in other environments without any heavy dependencies.

Actions should extend `WebAction`, be annotated with a HTTP method like `@Post`, accept a
request object and return a response object. Throw an exception like `BadRequestException` to
fail the request without much boilerplate.


### misk-api

High level interfaces and data classes which are implemented by both misk and wisp modules.
This module is agnostic to implementation details.


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


### misk-core

A collection of utility functions and interfaces that are used in many places.


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


### wisp.*

These modules were created to extract specific pieces of functionality out of the `misk*` modules
into new, low-dependency modules. They were especially focused on having no Guice dependencies.
Some of these modules duplicate existing Misk functionality,
but over time implementations will be deduplicated as part of broader code cleanup efforts.
