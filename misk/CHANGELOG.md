Change Log
==========

Version 0.2.0 *(2018-03-13)*
----------------------------

New:
Add \_status action
Split Interceptor into NetworkInterceptor and ApplicationInterceptor
Introduce websocket support in misk
Add tracing interceptor for web actions
Add ActionExceptionLogLevelConfig to control log levels for ActionExceptions
Allow binding an ExceptionMapper by an Exception type
Google Cloud Datastore and Cloud Storage support
Add backend for Zipkin tracer
Add MiskTracer to facilitate ad-hoc method tracing
Retrofit based typed client support (#112)
Enable SSL for both clients and servers (#111)
Support application/x-www-form-urlencoded parameters (#97)
Add API for injecting dynamically sourced flags
Add metrics backend for SignalFx
Add support for commands
Upgrade to Gradle 4.5 to support Java 9; add Java 9 to test matrix
Allow customized exception mappings
Add support for query strings in urls

Fix:
Eliminate redundant \_config suffix in config files
Support Web actions that return Nothing
Use proper snake-casing for default property names
Fix Java path param dispatching
Fix NotFoundAction handling (#134)
Fix wildcard based content routing
Fix a bug when a user-defined Interceptor returns a Response object
Fix up Java translation from Moshi. (#107)

Version 0.1.0 *(2018-02-01)*
----------------------------

Initial release.
