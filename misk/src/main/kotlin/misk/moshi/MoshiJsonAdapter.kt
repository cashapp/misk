package misk.moshi

import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

@Qualifier
@BindingAnnotation
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class MoshiJsonAdapter
