package misk.web.interceptors

import jakarta.inject.Qualifier
import misk.web.NetworkInterceptor

/**
 * Denotes a target to be in the first order of execution before any content decoding happens. A [NetworkInterceptor]
 * bound with [BeforeContentEncoding] is automatically installed before interceptors annotated with
 * [ForContentEncoding].
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.TYPE)
annotation class BeforeContentEncoding
