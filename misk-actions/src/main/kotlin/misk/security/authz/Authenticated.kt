package misk.security.authz

/**
 * Annotation indicating that a given action requires an authenticated caller - either a human
 * in a specific capability, or one of a set of services
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Authenticated(
  val services: Array<String> = [],
  val capabilities: Array<String> = []
)

/**
 * Annotation indicating that a given action supports unauthenticated access
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Unauthenticated
