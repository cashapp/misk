<img src="https://github.com/cashapp/misk/raw/master/misk.png" width="300">

* Releases
  * See most recent [public build][snap]
  * [change log][changelog]
  * API is subject to change

* Documentation
  * [Project Website][misk]
  * [Getting Started](./docs/getting-started.md)
  * [Developer Guide](./docs/developer-guide.md)

* Related Projects
  * [misk-web][miskweb]
  * [wisp](./wisp/README.md): wisp is now a part of Misk

# Overview
## What is Misk
Misk (Microservice Container in Kotlin) is an open source microservice container from Cash App.
It allows you to quickly create a microservice in Kotlin or Java, and provides libraries for common
concerns like serving endpoints, caching, queueing, persistence, distributed leasing and clustering.
It also includes [the Wisp library](./wisp/README.md), which is a collection of Kotlin modules
providing various features and utilities, including config, logging, feature flags and more.

It has been powering hundreds of microservices within Cash App since 2018.

## A Tour of Features
* Server
  * Built on top of [Jetty](https://eclipse.dev/jetty/)
  * HTTP/2 and gRPC support
  * Configurable through YAML
  * Service management through [Guava](https://github.com/google/guava/wiki/ServiceExplained)
  * Web-based admin console
* Client
  * Configurable HTTP clients built on top of [OkHttp](https://github.com/square/okhttp)
    and [Retrofit](https://github.com/square/retrofit)
  * gRPC clients built on top of [Wire](https://github.com/square/wire)
* Cloud integration
  * AWS: SQS, S3, DynamoDB
  * Google Cloud: Spanner
* Scheduled Jobs with cron syntax
* Persistence
  * ORM: Hibernate
  * JDBC: Connection pooling through [Hikari](https://github.com/brettwooldridge/HikariCP)
* Cache: redis client
* Metrics:
  * Integration with prometheus
  * Built-in metrics for JVM performance, networking and connection pooling
* Testing framework with annotations
* Kotlin utilities with minimal dependencies: Wisp

## What's Next?
Want to jump right into it? Check out our [Getting Started Guide](./docs/getting-started.md).

What to know more about each module, see [Developer Guide](./docs/developer-guide.md).

[changelog]: http://cashapp.github.io/misk/changelog/
[misk]: https://cashapp.github.io/misk/
[miskweb]: https://github.com/cashapp/misk-web/
[snap]: https://mvnrepository.com/artifact/com.squareup.misk/misk
