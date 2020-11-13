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
