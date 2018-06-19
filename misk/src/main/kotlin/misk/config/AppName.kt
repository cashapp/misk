package misk.config

import javax.inject.Qualifier

//@TODO make sure same targets everywhere

@Qualifier
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER
)
annotation class AppName
