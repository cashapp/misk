package misk.web.interceptors

import misk.web.NetworkInterceptor
import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

/**
 * Denotes a target to be in the first order of execution before any content decoding happens.
 * A [NetworkInterceptor] bound with [BeforeContentEncoding] is automatically
 * installed before interceptors annotated with [ForContentEncoding].
 */
@Qualifier
@BindingAnnotation
@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.FIELD,
  AnnotationTarget.TYPE
)
annotation class BeforeContentEncoding
