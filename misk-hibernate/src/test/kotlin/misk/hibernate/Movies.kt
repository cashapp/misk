package misk.hibernate

import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

@Qualifier
@BindingAnnotation
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class Movies

@Qualifier
@BindingAnnotation
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class MoviesReader
