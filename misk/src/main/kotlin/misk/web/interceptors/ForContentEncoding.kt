package misk.web.interceptors

import jakarta.inject.Qualifier
import misk.web.NetworkInterceptor

/**
 * Denotes a target interceptor to handle a message payload represented by a possible list of encoding found in the
 * "Content-Encoding" header value. A [NetworkInterceptor] bound with [ForContentEncoding] is automatically installed
 * after [BeforeContentEncoding] annotated interceptors.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.TYPE)
internal annotation class ForContentEncoding
