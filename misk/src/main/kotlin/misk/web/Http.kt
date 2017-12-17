package misk.web

@Target(AnnotationTarget.FUNCTION)
annotation class Get(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class Post(val pathPattern: String)

@Target(AnnotationTarget.FUNCTION)
annotation class JsonResponseBody

@Target(AnnotationTarget.FUNCTION)
annotation class PlaintextResponseBody

@Target(AnnotationTarget.FUNCTION)
annotation class ProtobufResponseBody

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class JsonRequestBody

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestHeaders

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PathParam(val value: String)
