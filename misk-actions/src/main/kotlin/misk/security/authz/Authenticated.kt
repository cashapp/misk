package misk.security.authz

/**
 * Annotation indicating that a given action requires an authenticated caller - either a human
 * in a specific capability, or one of a set of services
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Authenticated(
  /** Calling services must be listed here to be authenticated */
  val services: Array<String> = [],

  /** Calling users must have at least one of these capabilities to be authenticated */
  val capabilities: Array<String> = []
)

/**
 * Annotation indicating that a given action supports unauthenticated access
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Unauthenticated

/**
 * Annotation indicating that any authenticated service is allowed to access this endpoint.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class AllowAnyService

/**
 * Annotation indicating that any authenticated service is allowed to access this endpoint.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class AllowAnyUser
