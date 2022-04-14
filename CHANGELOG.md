Change Log
==========

Version 0.24.0 *(2022-04-13)*
----------------------------

Unstable public release.

New features and fixes:

- Fill out the Misk BOM ([#2353](https://github.com/cashapp/misk/pull/2353))
- Restore a deleted WebConfig constructor ([#2352](https://github.com/cashapp/misk/pull/2352))
- enable kochiku in CI ([#2351](https://github.com/cashapp/misk/pull/2351))

Version 0.23.0 *(2022-03-30)*
-----------------------------

Unstable public release. Thank you to all the contributors, as always.

Breaking changes:

- Upgrade to use kotlin 1.6 ([#2323](https://github.com/cashapp/misk/pull/2323))
- Move ActionScope and related code to :misk-action-scopes ([#2301](https://github.com/cashapp/misk/pull/2301))
- Remove misk.flags.Flags ([#2341](https://github.com/cashapp/misk/pull/2341))
- misk-metrics: Allow configuring max_age_in_seconds for Prometheus ([#2292](https://github.com/cashapp/misk/pull/2292))

New features:

- misk-actions: Multi-method WebActions ([#2198](https://github.com/cashapp/misk/pull/2198))
- misk-clients: Allow binding for application interceptors in grpc and typed clients ([#2201](https://github.com/cashapp/misk/pull/2201))
- misk-core: Support a configurable delay before shutting down services ([#2238](https://github.com/cashapp/misk/pull/2238))
- misk-core: Allow minimum thread pool size to be configured ([#2210](https://github.com/cashapp/misk/pull/2210))
- misk-core: Record client metrics for incomplete HTTP connections ([#2209](https://github.com/cashapp/misk/pull/2209))
- misk-jdbc: Provide more helpful error message when duplicate migration versions present ([#2325](https://github.com/cashapp/misk/pull/2325))
- misk-jdbc: Default to use modern TLS in JDBC connections ([#2221](https://github.com/cashapp/misk/pull/2221))
- misk-hibernate: add support for FlushEntity listener ([#2220](https://github.com/cashapp/misk/pull/2220))
- misk-feature: Make FakeFeatureFlag overrides composable with FakeFeatureFlagsOverrideModule ([#2306](https://github.com/cashapp/misk/pull/2306))
- misk-feature, wisp-feature: Add strongly typed feature flag support ([#2293](https://github.com/cashapp/misk/pull/2293))
- misk-feature, wisp-feature: Launch Darkly client will use system proxy settings if provided ([#2255](https://github.com/cashapp/misk/pull/2255))
- misk-redis: Implement Redis.hincrBy ([#2269](https://github.com/cashapp/misk/pull/2269))
- misk-zookeeper, wisp-lease: Extend LeaseManager to support Leases as AutoCloseable resources ([#2319](https://github.com/cashapp/misk/pull/2319))
- wisp-config: Add a filesystem preprocessor ([#2275](https://github.com/cashapp/misk/pull/2275))
- wisp-config: Add a classpath preprocessor ([#2268](https://github.com/cashapp/misk/pull/2268))

Fixes:

- misk-admin: Web Actions Tab: handle long primitive when building web forms ([#2300](https://github.com/cashapp/misk/pull/2300))
- misk-clients: Do not require `List<Client*Interceptor>` to be bound when using TypedClientFactory stand-alone ([#2218](https://github.com/cashapp/misk/pull/2218))
- misk-core: Improve handling timeout/reset connection when reading request ([#2279](https://github.com/cashapp/misk/pull/2279))
- misk-core: Configure VegasLimit with higher initial limit ([#2250](https://github.com/cashapp/misk/pull/2250))
- misk-gcp: Properly shut down Google Spanner clients ([#2203](https://github.com/cashapp/misk/pull/2203))
- misk-hibernate: Move StartDatabaseService init code to explicit function ([#2294](https://github.com/cashapp/misk/pull/2294))
- misk-jooq: Use appropriate SQLDialect per configuration ([#2305](https://github.com/cashapp/misk/pull/2305))
- misk-jooq: Don't use "select * from" ([#2231](https://github.com/cashapp/misk/pull/2231))


Version 0.22.0 *(2021-11-03)*
----------------------------

Unstable public release.

Breaking changes:

- The `/error` action is no longer installed by default (#2190)

New features and fixes:

- Ignore @transient fields when constructing queries (#2162)
- Add support for Google Spanner to misk-gcp (#2188)
- Add a default Moshi build in wisp, and move the builder from Misk (#2187)
- Add wisp-token* - copied from misk.tokens (#2186)
- Allow invalid accept headers (#2185)
- Put unack'd jobs on the deadletter queue and remove FakeTransactionalJobQueue (#2180)
- Remove internal visibility modifier from PrometheusHttpService (#2181)
- Add wisp-tracing module (#2171)

Version 0.21.0 *(2021-10-18)*
----------------------------

Unstable public release.

Version 0.20.0 *(2021-08-31)*
----------------------------

Unstable public release.

Breaking changes:

* Leases now use a different package
* Wisp Lease API is changing to handle explicit acquire/release on lease (#2113)
* Log on properties present in config yaml but not in object (#2118)
* Config now requires defaults for primitive types
* Move misk admin components to misk-admin (#2065)
* Reinstate WebActionExceptionMapper sending a response body based on the WebActionException's responseBody (#2050)

New features and fixes:

* Add incr and incrBy operations to misk-redis (#2119)
* Add @fetch as a valid hibernate query annotation (#2112)
* When evaluating JSON LD feature flags, log unknown fields once (#2086)
* Make additional jooq configurations possible (#2078)
* Log a warning if health checks fail (#2063)
* Add support for double feature flags (#2029)
* Send gRPC errors properly (#1983)
* Bump log level for invalid access (#2024)
* misk-policy: To query specific paths, treat document path as urlencoded (#2030)
* Create LaunchDarkly in Wisp (#2088)

Version 0.19.0 *(2021-06-30)*
----------------------------

Unstable public release.

Version 0.18.0 *(2021-06-28)*
----------------------------

Unstable public release.

Version 0.17.1 *(2021-04-29)*
----------------------------

Unstable public release.

Version 0.17.0 *(2021-04-28)*
----------------------------

Unstable public release.


Version 0.16.0 *(2020-12-17)*
----------------------------

Unstable public release.


Version 0.15.0 *(2020-12-03)*
----------------------------

Unstable public release.


Version 0.14.0 *(2020-11-12)*
----------------------------

Unstable public release.


Version 0.13.0 *(2020-07-16)*
----------------------------

Unstable public release.


Version 0.12.0 *(2020-05-06)*
----------------------------

Unstable public release.


Version 0.11.0 *(2020-02-25)*
----------------------------

Unstable public release.


Version 0.10.0 *(2019-01-21)*
----------------------------

Unstable public release.


Version 0.9.0 *(2019-12-06)*
----------------------------

Unstable public release.


Version 0.8.0 *(2019-10-22)*
----------------------------

Unstable public release.


Version 0.7.0 *(2019-08-26)*
----------------------------

Unstable public release.


Version 0.2.5 *(2018-06-11)*
----------------------------

### New

* Cluster interface and DataSourceCluster bindings
* Add a JPAEntityModule for binding entities for a DataSource
* Hook up raw Hibernate APIs
* Use JPA entity types in HibernateModule.
* Introduce FakeResourceLoader
* SchemaMigrator for running and tracking schema migrations.
* Update Misk version in Dockerfiles to 0.2.5
* Drop support for unqualified datasources.
* move exemplars into sample directory in preparation for more of them
* Make all Kotlin warnings build errors
* Allow services to specify dependencies on other services.
* Fast fail on dependency cycles.
* jre8 was deprecated for kotlin 1.2
* Early types for the Transacter APIs
* Queries in the Misk Hibernate API.
* Implement Query with dynamic proxies and reflection
* Offer strict validation and nice errors in ReflectionQueryFactory
* Log the reasons why liveness/readiness checks fail
* URL shortener sample
* Support more operators in Query
* Wire up Hibernate event listeners through Guice.
* HibernateTestingModule.
* Switch tests to MySQL
* Rollback transactions on exceptions
* Delete DataSourceModule. It's redundant with HibernateModule.
* Support ByteString columns
* Misk containers should not run as root
* DbTimestampedEntity

### Fix

* Don't inject until after services are started.
* Tidy up some test cases.
* Fix a missing dependency in exemplar
* Don't use KubernetesHealthCheck with LocalClusterConnector

Version 0.2.4 *(2018-05-14)*
----------------------------

### New

* Add support for protobuf over HTTP
* Cloudwatch Trail logging support
* Add retry() helper
* Add Backoff/ExponentialBackoff
* MiskCaller and authz support
* Adds a DataSourceModule
* Add support for logging to StackDriver

### Fix

* Move static resources from web root into resources
* Move web-specific NetworkInterceptor into web
* Remove use of instance metadata endpoints

Version 0.2.3 *(2018-04-27)*
----------------------------

### New

* Add kubernetes java client so that hosts can know their peers
* Use EventRouter for exemplarchat. Add a static resource mapper
* Create a cluster wide admin healthcheck page
* Change CacheBuilder to be mapOf since no concurrency
* Adds a healthcheck for the kubernetes client
* Adds a local cluster connector so that development functions

### Fix

* Don't treat assembly as a release when running in CI
* Fix tracing startup when none is configured
* Various event router fixes and refactorings

Version 0.2.1 *(2018-03-26)*
----------------------------

### New

* Remove unnecessary check from uploadArchives task (#149)
* Add a RELEASING.md to outline misk release process (#150)
* Move chat into its own example project. (#146)
* Introduce event router api (#147)
* Add basic frontend for exemplarchat
* Add MoshiJsonAdapters and SocketEventJsonAdapter (#151)
* Expose client certificates as action scoped vars (#141)
* Add ClusterMapper interface, refactor RealEventRouter to event loop (#155)
* Handle cluster changes (#156)
* More tests to exercise EventRouter behaviors. (#158)
* Support loading keystores from combined private key and certificate chain PEM files (#157)

### Fix

* Fix event router tests (#159)

Version 0.2.0 *(2018-03-13)*
----------------------------

### New

* Add \_status action
* Split Interceptor into NetworkInterceptor and ApplicationInterceptor
* Introduce websocket support in misk
* Add tracing interceptor for web actions
* Add ActionExceptionLogLevelConfig to control log levels for ActionExceptions
* Allow binding an ExceptionMapper by an Exception type
* Google Cloud Datastore and Cloud Storage support
* Add backend for Zipkin tracer
* Add MiskTracer to facilitate ad-hoc method tracing
* Retrofit based typed client support (#112)
* Enable SSL for both clients and servers (#111)
* Support application/x-www-form-urlencoded parameters (#97)
* Add API for injecting dynamically sourced flags
* Add metrics backend for SignalFx
* Add support for commands
* Upgrade to Gradle 4.5 to support Java 9; add Java 9 to test matrix
* Allow customized exception mappings
* Add support for query strings in urls

### Fix

* Eliminate redundant \_config suffix in config files
* Support Web actions that return Nothing
* Use proper snake-casing for default property names
* Fix Java path param dispatching
* Fix NotFoundAction handling (#134)
* Fix wildcard based content routing
* Fix a bug when a user-defined Interceptor returns a Response object
* Fix up Java translation from Moshi. (#107)

Version 0.1.0 *(2018-02-01)*
----------------------------

Initial release.
