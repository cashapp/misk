package misk.config

import javax.inject.Qualifier

@Deprecated("Use from misk-config instead")
@Qualifier
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.VALUE_PARAMETER
)
annotation class AppName
