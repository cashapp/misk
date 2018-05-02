package misk.security.authz

import javax.inject.Qualifier

/**
 * Annotation indicating that a given action requires an authenticated caller - either a human
 * in a specific role, or one of a set of services
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Authenticated(val services: Array<String> = [], val roles: Array<String> = [])

/**
 * Annotation indicating that a given action supports unauthenticated access
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Unauthenticated

