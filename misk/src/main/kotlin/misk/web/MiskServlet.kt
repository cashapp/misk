package misk.web

import jakarta.inject.Qualifier

/** Qualifier annotation for the Misk WebActions servlet that can be embedded in external servlet containers. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class MiskServlet
