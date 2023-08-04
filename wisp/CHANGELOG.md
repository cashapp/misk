Change Log
==========
Version 1.4.12 *(2023-07-31)*
----------------------------
* Fixed certificate error. While parsing certificates, all fields except the part between BEGIN and END lines will be ignored.

Version 1.4.11 *(2023-07-18)*
----------------------------
* change the implementation of ld metrics ([#182](https://github.com/cashapp/wisp/pull/182)) 

Version 1.4.10 *(2023-07-11)*
----------------------------

* add logs for launchdarkly client startup


Version 1.4.9 *(2023-07-10)*
----------------------------

* docker: "3.3.0" -> "3.3.1"
* jnrUnixsocket: "0.38.19" -> "0.38.20"
* kotlinBinaryCompatibilityPlugin: "0.13.1" -> "0.13.2"
* kotlinx: "1.7.0" -> "1.7.2"
* logback: "1.4.7" -> "1.4.8"
* micrometer: "1.11.0" -> "1.11.1"
* mockito: "5.3.1" -> "5.4.0"
* moshi: "1.14.0" -> "1.15.0"
* versionsGradlePlugin: "0.46.0" -> "0.47.0"


Version 1.4.8 *(2023-06-28)*
----------------------------

* Add key validation for strong feature flags


Version 1.4.7 *(2023-06-09)*
----------------------------

* Remove unused api/platform dependencies (netty, grpc, jackson)


Version 1.4.6 *(2023-06-08)*
----------------------------

Dependency changes:

* netty: "4.1.92.Final" -> "4.1.93.Final"


Version 1.4.5 *(2023-06-06)*
----------------------------

* Fix ABI incompatibility for Launch Darkly Client


Version 1.4.4 *(2023-06-06)*
----------------------------

* Add metrics for ldClient initialization


Version 1.4.3 *(2023-05-30)*
----------------------------

* Deprecate wisp-tracing extensions: `Tracer.spanned`, `Tracer.scoped`, and `Tracer.childSpan`.
* Update wisp-tracing extensions to better support multithreaded traces, and to support optional baggage retention on independent traces.
* Update wisp-tracing documentation.
* Version 1.4.2 was a skipped release.


Version 1.4.1 *(2023-05-17)*
----------------------------

* Fix accidental inclusion of kotest runner in runtime dependencies.


Version 1.4.0 *(2023-05-11)*
----------------------------

Dependency changes:

* aws2: "2.18.4" -> "2.19.33"
* dependencyAnalysisPlugin: "1.19.0" -> "1.20.0"
* grpc: "1.54.0" -> "1.55.1"
* hoplite: "2.7.1" -> "2.7.4"
* jackson: "2.14.2" -> "2.15.0"
* junit: "5.9.2" -> "5.9.3"
* kotest: "5.5.5" -> "5.6.2"
* kotlin: "1.7.22" -> "1.8.21"
* kotlinBinaryCompatibilityPlugin: "0.13.0" -> "0.13.1"
* kotlinx: "1.6.4" -> "1.7.0"
* logback: "1.4.5" -> "1.4.7"
* micrometer: "1.10.4" -> "1.11.0"
* mockito: "4.9.0" -> "5.3.1"
* netty: "4.1.91.Final" -> "4.1.92.Final"
* okHttp: "4.10.0" -> "4.11.0"
* protobufGradlePlugin: "0.9.2" -> "0.9.3"
* slf4j: "2.0.6" -> "2.0.7"
* versionCatalogUpdateGradlePlugin: "0.7.0" -> "0.8.0"
* versionsGradlePlugin: "0.45.0" -> "0.46.0"


Version 1.3.12 *(2023-04-20)*
----------------------------

* Ensure published modules are targeting java 11


Version 1.3.11 *(2023-04-20)*
----------------------------

* Add the dependencyAnalysisPlugin to help make our build.gradle files better


Version 1.3.10 *(2023-04-12)*
----------------------------

Dependency changes:

* netty: "4.1.89.Final" -> "4.1.91.Final"
* grpc: "1.53.0" -> "1.54.0"
* launchDarkly: "5.10.6" ->"5.10.7"


Version 1.3.9 *(2023-03-15)*
----------------------------

* Make RateLimiter, PercentageSampler, RateLimitingSampler, AlwaysSampler, Sleeper, and Ticker public
* Make FakeFeatureFlags open
* Make ResourceLoader and SslLoader open
* Disallow setting both url and envoy client configs
* Make SampledLogger and FakeClock open

Dependency changes:

* gradle: "7.6" -> "7.6.1"
* assertj: "3.23.1" -> "3.24.2"


Version 1.3.8 *(2023-02-23)*
----------------------------

Dependency changes:

* micrometer: "1.9.2" -> "1.10.4"


Version 1.3.7 *(2023-02-20)*
----------------------------

Dependency changes:

* docker: "3.2.14" -> "3.3.0"
* micrometer: "1.8.9" -> "1.9.2"
* netty: "4.1.87.Final" -> "4.1.89.Final"
* okio: "3.2.0" -> "3.3.0"


Version 1.3.6 *(2023-02-10)*
----------------------------

Dependency changes:

* grpc: "1.52.1" -> "1.53.0"
* hoplite: "2.7.0" -> "2.7.1"
* jackson: "2.14.1" -> "2.14.2"
* kotest: "5.5.4" -> "5.5.5"
* kotlinLogging: "3.0.4" -> "3.0.5"
* protobufGradlePlugin: "0.9.1" -> "0.9.2"
* slf4j: "2.0.5" -> "2.0.6"
* versionsGradlePlugin: "0.44.0" -> "0.45.0"


Version 1.3.5 *(2023-02-02)*
----------------------------

* Make withTags logging function public


Version 1.3.4 *(2023-01-18)*
----------------------------

* Upgraded publishing plugin, fixed and improved publishing process

Dependency changes:

* grpc: "1.51.0" -> "1.52.1"
* netty: "4.1.85.Final" ->"4.1.87.Final"


Version 1.3.3 *(2023-01-16)*
----------------------------

* Remove the percentile histogram setting and keep as a summary (repeated task metrics)


Version 1.3.2 *(2023-01-13)*
----------------------------

* Rename the duration summary metric to be more meaningful


Version 1.3.1 *(2023-01-10)*
----------------------------

* Record metric labels for repeated tasks.


Version 1.3.0 *(2023-01-09)*
----------------------------

* launchDarkly: "5.8.1" ->"5.10.6"


Version 1.2.8 *(2023-01-09)*
----------------------------

* Add retryOnConnectionFailure config option for HttpClientFactory 


Version 1.2.7 *(2022-12-15)*
----------------------------

* Add releaseAll to LeaseManager interface


Version 1.2.6 *(2022-12-13)*
----------------------------

* Version 1.2.5 did not release properly - missing modules


Version 1.2.5 *(2022-12-08)*
----------------------------

* Typo fix

Dependency changes:

* aws2: "2.17.267" ->"2.18.4"
* docker: "3.2.13" -> "3.2.14"
* grpc: "1.50.0" -> "1.51.0"
* hoplite: "2.6.5" -> "2.7.0"
* jackson: "2.13.4" ->"2.14.1"
* jnrUnixsocket: "0.38.17" -> "0.38.19"
* kotest: "5.5.1" ->"5.5.4"
* kotlin: "1.7.20" -> "1.7.22"
* kotlinBinaryCompatibilityPlugin: "0.11.1" -> "0.12.1"
* kotlinLogging: "3.0.2"-> "3.0.4"
* logback: "1.4.4" -> "1.4.5"
* mockito: "4.8.0" ->"4.9.0"
* netty: "4.1.82.Final" ->"4.1.85.Final"
* prometheus: REMOVED prometheusClient
* protobufGradlePlugin: "0.8.19" -> "0.9.1"
* slf4j: "2.0.3" -> "2.0.5"
* versionsGradlePlugin: "0.43.0" -> "0.44.0"


Version 1.2.4 *(2022-11-18)*
----------------------------

* Add ability to increment the `FakeClock` by a `java.time.Period`


Version 1.2.3 *(2022-11-03)*
-----------------------------

* Fix: Pooled lease release needs to call the delegate lease release.


Version 1.2.2 *(2022-10-28)*
-----------------------------

* Remove dependency constraint on io.prometheus:simpleclient that forced version 0.9.0 on consumers

Version 1.2.1 *(2022-10-19)*
-----------------------------

* Upgrade dependencies

Dependency changes:

* grpc: "1.49.2" -> "1.50.0"
* hoplite: "2.6.4" -> "2.6.5"
* kotest: "5.5.0" -> "5.5.1"
* kotlin: "1.7.10" -> "1.7.20"
* kotlinLogging: "3.0.0" -> "3.0.2"
* logback: "1.4.3" -> "1.4.4"
* versionCatalogUpdateGradlePlugin: "0.6.1" -> "0.7.0"
* versionsGradlePlugin: "0.42.0" -> "0.43.0"

Version 1.2.0 *(2022-10-18)*
-----------------------------

* Pooled Leases implementation


Version 1.1.0 *(2022-10-14)*
-----------------------------

* Add wisp.sampling module
* SampledLogger for wisp.logging

Dependency changes:

* aws2: "2.17.224" -> "2.17.267"
* grpc: "1.49.0" -> "1.49.2"
* hoplite: "2.6.2" -> "2.6.4"
* jackson: "2.13.3" -> "2.13.4"
* junit: "5.9.0" -> "5.9.1"
* kotest: "5.4.2" -> "5.5.0"
* kotlinBinaryCompatibilityPlugin: "0.11.0" -> "0.11.1"
* kotlinLogging: "2.1.23" -> "3.0.0"
* logback: "1.4.0" -> "1.4.3"
* mockito: "4.7.0" -> "4.8.0"
* moshi: "1.13.0" -> "1.14.0"
* netty: "4.1.80.Final" -> "4.1.82.Final"
* slf4j = "2.0.0" -> "2.0.3"


Version 1.0.10 *(2022-09-26)*
-----------------------------

* Fix potential NPE in wisp-resource-loader
* Allow all tracer extensions to return values
* Apply the AWS BOM to prevent AWS lib version mismatches

Dependency changes:

* grpc: 1.48.1 -> 1.49.0
* hoplite: 2.5.2 -> 2.6.2
* logback: 1.2.11 -> 1.4.0
* micrometer: 1.8.4 -> 1.8.9
* netty: 4.1.79.Final -> 4.1.80.Final
* slf4j: 1.7.36 -> 2.0.0
* versionCatalogUpdateGradlePlugin: 0.5.3 -> 0.6.1


Version 1.0.9 *(2022-08-29)*
----------------------------

* Pin AWS version to 2.17.224
* Roll back AWS lib version due to issue with Flink apps. See commit: 5b0be75d2438d25b5e1950bfbdb6d58b39e99274


Version 1.0.8 *(2022-08-18)*
----------------------------

* Use gradle catalog for dependency versions
* Add gradle plugins for dependency updates checking and applying changes 
* Dependency clean up in various modules

Dependency changes:

* aws: 2.17.234 -> 2.17.254
* hoplite: 2.3.3 -> 2.5.2
* junit: 5.8.2 -> 5.9.0
* kotest: 5.3.2 -> 5.4.2
* mockito: 4.6.1 -> 4.7.0
       

Version 1.0.7 *(2022-08-08)*
----------------------------

* Add getJsonString to DynamicConfig as well


Version 1.0.6 *(2022-08-05)*
----------------------------

* Return JSON string without converting into an object from LD client


Version 1.0.5 *(2022-07-21)*
----------------------------

* Gradle 7.5
* Revert io.micrometer:micrometer-registry-prometheus to 1.8.4


Version 1.0.4 *(2022-07-21)*
----------------------------

* Upgrade dependencies and fix test for the library change

Dependency changes:

* org.assertj:assertj-core:3.20.2 -> 3.23.1
* software.amazon.awssdk:regions:2.17.198 -> 2.17.234
* org.bouncycastle:bcprov-jdk15on:1.69 -> 1.70
* com.sksamuel.hoplite:hoplite-\*:2.1.5 -> 2.3.3
* com.github.jnr:jnr-unixsocket:0.38.8 -> 0.38.17
* org.jetbrains.kotlinx:binary-compatibility-validator:0.10.1 -> 0.11.0
* org.jetbrains.kotlin:kotlin-bom:1.7.0 -> 1.7.10
* org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0 -> 1.7.10
* org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3 -> 1.6.4
* ch.qos.logback:logback-classic:1.2.3 -> 1.2.11
* io.micrometer:micrometer-registry-prometheus:1.8.4 -> 1.9.2
* org.mockito:mockito-core:3.11.2 -> 4.6.1
* io.netty:netty-bom:4.1.74.Final -> 4.1.79.Final
* com.squareup.okhttp3:okhttp:4.10.0-RC1 -> 4.10.0
* com.squareup.okio:okio:3.1.0 -> 3.2.0"
* com.google.protobuf:protobuf-gradle-plugin:0.8.17 -> 0.8.19
* org.slf4j:slf4j-api:1.7.32 -> 1.7.36"


Version 1.0.3 *(2022-07-19)*
----------------------------

* Drop versions from kotlin BOM-listed artifacts
* Stop exporting the kotlinBom as an api dependency
* Remove unused dependencies

Version 1.0.2 *(2022-07-13)*
----------------------------

* fix: init file system watcher lazily
* fix: kotlin1.7 support for the build


Version 1.0.1 *(2022-07-08)*
----------------------------

* feature: Upgrade to kotlin 1.7
* fix: cashapp not squareup for urls


Version 1.0.0 *(2022-06-22)*
----------------------------

Initial release.

Ready for Production use.


Version 0.0.1-alpha *(2022-06-17)*
----------------------------------

Initial publishing test release.

Not for Production use.
