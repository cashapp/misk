package misk.web

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Get(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Post(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Put(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Grpc(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Delete(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Patch(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class ConnectWebSocket(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS) annotation class Description(val text: String)

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestHeaders

/**
 * Extracts the named request header as a `String` or a `String?`. If the parameter is not nullable, and has no default
 * value, and the header is absent, the request will fail with an HTTP 400.
 */
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestHeader(val value: String)

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestCookies

/**
 * Extracts the named request cookie as a `String` or a `String?`. If the parameter is not nullable, and has no default
 * value, and the cookie is absent, the request will fail with an HTTP 400.
 */
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestCookie(val value: String)

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class PathParam(val value: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class QueryParam(val value: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class FormValue

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class FormField(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestBody

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class RequestContentType(vararg val value: String)

/**
 * Indicates what response content types the action can produce.
 *
 * Clients can specify what content type they prefer by setting the `Accept` header. If the action supports multiple
 * content types but no `Accept` header is specified, the first content type is used.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class ResponseContentType(vararg val value: String)

/**
 * Enables unframed protobuf POST requests to a gRPC [misk.web.actions.WebAction]. When present, the framework
 * automatically creates a protobuf POST variant (`application/x-protobuf`) in addition to the existing JSON variant.
 * This allows a single gRPC action to accept gRPC, JSON POST, and protobuf POST requests without defining a separate
 * `WebAction` class.
 *
 * Works with both [com.squareup.wire.WireRpc] and [Grpc] actions. Only applies to unary gRPC actions (single request
 * parameter). Streaming actions are not supported because they require gRPC framing.
 *
 * Usage with [com.squareup.wire.WireRpc] via Wire-generated server interface:
 * ```
 * class MyAction @Inject constructor() : GreeterSayHelloBlockingServer, WebAction {
 *   @EnableUnframedRequests
 *   override fun SayHello(request: HelloRequest): HelloReply { ... }
 * }
 * ```
 *
 * Usage with [Grpc]:
 * ```
 * class MyAction @Inject constructor() : WebAction {
 *   @Grpc
 *   @EnableUnframedRequests
 *   fun sayHello(request: HelloRequest): HelloReply { ... }
 * }
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EnableUnframedRequests

/**
 * When the service is overloaded Misk will intervene and reject calls by returning "HTTP 503 Service Unavailable". We
 * call this load shedding and it works similarly to flow control in TCP.
 *
 * We must not shed calls to status endpoints like health checks as doing so may create cascading failures: overloaded
 * instances that do not respond to health checks will be killed, and this further overloads the remaining peers.
 *
 * Only put this on endpoints that must never be rejected. Such endpoints must not do RPCs, database queries or other
 * I/O because unexpected latency there can take down the entire service.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class AvailableWhenDegraded
