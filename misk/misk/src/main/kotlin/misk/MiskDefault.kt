package misk

import com.google.inject.BindingAnnotation
import misk.web.interceptors.JsonInterceptorFactory
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Denotes a target as a Misk provided default, which will be automatically installed.
 * For example, the [JsonInterceptorFactory] is bound with [MiskDefault] and is automatically
 * installed.
 */
@BindingAnnotation
@Retention(RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
internal annotation class MiskDefault
