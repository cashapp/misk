package misk.web.interceptors

/**
 * Annotation indicating that request and response information should be logged.
 *
 * sampling is used to sample the number of requests logged with 0.0 for none and 1.0 for all.
 * Valid values are in the range (0.0, 1.0].
 *
 * If includeBody is true both the action arguments and the response will be logged.
 *
 * If arguments and responses may include sensitive information, it is expected that the toString()
 * methods of these objects will redact it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class LogRequestResponse(val sampling: Double, val includeBody: Boolean)
