package misk.annotation

/**
 * Marks declarations that are experimental and subject to change without following SemVer conventions. Both binary and
 * source-incompatible changes are possible, including complete removal of the experimental API.
 *
 * Do not use these APIs in modules that may be executed using a version of Misk different from the version the module
 * was compiled with.
 *
 * Do not use these APIs in published libraries.
 *
 * Do not use these APIs if you aren't willing to track changes to them.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.ANNOTATION_CLASS,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.LOCAL_VARIABLE,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.CONSTRUCTOR,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.TYPEALIAS,
)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class ExperimentalMiskApi
