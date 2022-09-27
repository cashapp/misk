package misk.web.interceptors

/**
 * Annotation indicating that request and response information should be logged.
 *
 * Rate limiting is used to sample the number of requests logged. The value specified is the
 * log events per sec allotted per action.
 *
 * By default we set rate limiting for successes to 10 log events per second,
 * enough to show things are happening without sending too many logs. By default
 * rate limiting is off for errors, as we would want to surface such logs
 * for investigation.
 *
 * If you would like to turn off rate limiting and emit all logs, set ratePerSecond and/or
 * errorRatePerSecond to 0.
 *
 * Percentage sampling is used to sample request and response bodies, with 0.0 for none and 1.0 for all.
 * Valid values are in the range [0.0, 1.0].
 *
 * You can disable logging in particular environments by using the all-lowercase names of the environments.
 * See the wisp-deployment module for details of supported environment names.
 *
 * If arguments and responses may include sensitive information, it is expected that the toString()
 * methods of these objects will redact it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class LogRequestResponse(
  val ratePerSecond: Long = 10,
  /** By default, rate limiting is off for error logs **/
  val errorRatePerSecond: Long = 0,
  /** By default do not log request and response bodies **/
  val bodySampling: Double = 0.0,
  val errorBodySampling: Double = 0.0,
  /** which deploy environments will not have request/response logging enabled **/
  val disabledEnvironments: Array<String> = [],
)
