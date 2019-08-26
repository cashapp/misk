Change Log
==========

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
