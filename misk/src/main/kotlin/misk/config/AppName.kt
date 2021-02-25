package misk.config

import javax.inject.Qualifier

@Qualifier
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.VALUE_PARAMETER
)
annotation class AppName
