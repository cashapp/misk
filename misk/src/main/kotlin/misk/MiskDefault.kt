package misk

import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Denotes a target as a Misk provided default, which will be automatically installed.
 * For example, the [MarshallerInterceptor] is bound with [MiskDefault] and is automatically
 * installed.
 */
@Qualifier
@BindingAnnotation
@Retention(RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.FIELD,
  AnnotationTarget.TYPE
)
internal annotation class MiskDefault
