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
 *
 * Rate limiting is used to sample the number of requests logged. The value specified is the
 * log events per sec allotted per caller per action.
 *
 * By default we set rate limiting for both successes and errors to 1 log event per sec,
 * enough to show things are happening without sending too many logs.
 *
 * If you would like to turn off rate limiting and emit all logs, set rateLimiting and/or
 * errorRateLimiting to 0.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class LogRequestResponse(
  // TODO(isabel): Remove sampling and includeBody. Will be replaced with rate limiting
  val sampling: Double,
  val includeBody: Boolean,
  // Currently unused
  val rateLimiting: Long = 1,
  val errorRateLimiting: Long = 1)
