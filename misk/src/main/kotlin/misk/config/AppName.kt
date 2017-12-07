package misk.config

import com.google.inject.BindingAnnotation

@BindingAnnotation
@Target(
        AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.VALUE_PARAMETER
)
annotation class AppName
