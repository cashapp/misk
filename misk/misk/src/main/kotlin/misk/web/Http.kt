package misk.web

@Target(AnnotationTarget.FUNCTION)
annotation class Get(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Post(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class ConnectWebSocket(val pathPattern: String)

/**
 * For GRPC actions the path is formatted as `/<service name>/<method name>`. The path pattern of
 * the proto service below is `/squareup.helloworld.Greeter/SayHello`.
 *
 * ```
 * package squareup.helloworld;
 *
 * service Greeter {
 *   rpc SayHello (HelloRequest) returns (HelloReply) {}
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Grpc(val pathPattern: String)

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
