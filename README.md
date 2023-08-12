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
  * [wisp][wisp]: wisp now is a part of Misk
 

# Overview
## What is Misk
Misk (Microservice Container in Kotlin) is an open source microservice container from Cash App.
It allows you to quickly create a microservice in Kotlin or Java, and provides libraries for common
concerns like serving endpoints, caching, queueing, persistence, distributed leasing and clustering.
It also includes [the Wisp library](./wisp/README.md), which is a collection of Kotlin modules
providing various features and utilities, including config, logging, feature flags and more.

It has been powering hundreds microservices within Cash App since 2018.

## A Tour of Features
* Cloud Native
 * AWS
 * Google Cloud
* Configuration using yaml
* Server
* Client
  * gRPC client
  * httpClient
* Job Queue
* Cron Jobs
* Web Actions
* Admin console
* Hibernate
* Cache: redis access using Jedis
* Metrics: integration with prometheus
* A collection of utilities: Wisp

## What's Next?
Want to jump right into it? Check out our [Getting Started](./docs/getting-started.md).

What to know more about each module, see [Developer Guide](./docs/developer-guide.md) 

[changelog]: http://cashapp.github.io/misk/changelog/
[misk]: https://cashapp.github.io/misk/
[miskweb]: https://cashapp.github.io/misk-web/
[snap]: https://mvnrepository.com/artifact/com.squareup.misk/misk
[wisp]: https://github.com/cashapp/wisp
