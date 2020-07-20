package misk.web

@Target(AnnotationTarget.FUNCTION)
annotation class Get(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Post(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Put(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Delete(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Patch(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class ConnectWebSocket(val pathPattern: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestHeaders

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PathParam(val value: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class QueryParam(val value: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FormValue

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FormField(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestBody

@Target(AnnotationTarget.FUNCTION)
annotation class RequestContentType(vararg val value: String)

@Target(AnnotationTarget.FUNCTION)
annotation class ResponseContentType(val value: String)

/**
 * When the service is overloaded Misk will intervene and reject calls by returning "HTTP 503
 * Service Unavailable". We call this load shedding and it works similarly to flow control in TCP.
 *
 * We must not shed calls to status endpoints like health checks as doing so may create cascading
 * failures: overloaded instances that do not respond to health checks will be killed, and this
 * further overloads the remaining peers.
 *
 * Only put this on endpoints that must never be rejected. Such endpoints must not do RPCs, database
 * queries or other I/O because unexpected latency there can take down the entire service.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class AvailableWhenDegraded

/**
 * Opt-in to concurrency limits. If the service is overloaded permit Misk to shed calls to this
 * endpoint by returning "HTTP 503 Service Unavailable".
 *
 * In a future release of Misk this will be unnecessary because concurrency limits will be on by
 * default.
 *
 * TODO(jwilson): deprecate this when it becomes unnecessary.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ConcurrencyLimitsOptIn

/**
 * Opt-out of concurrency limits. Misk will not shed calls to this endpoint even if the service is
 * overloaded.
 *
 * In a future release this annotation will be deprecated. Developers will need to decide how this
 * action responds when the service is degraded:
 *
 *  * The action is eligible for concurrency limits. In this case the annotation can safely be
 *    removed. Most services should do this.
 *
 *  * The action is not eligible for concurrency limits. In this case the [AvailableWhenDegraded]
 *    annotation should be used instead. Most services should not do this.
 *
 * TODO(jwilson): deprecate this when make concurrency limits on by default.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ConcurrencyLimitsOptOut
