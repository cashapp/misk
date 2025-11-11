package misk.web.interceptors

/**
 * Triggers an entry and return log for each Interceptor that proceeds
 * through traffic inside RealNetworkChain.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class LogNetworkInterceptorChain(
  /** If the log before the interceptor is called should be logged */
  val logBefore: Boolean = true,
  /** If the log after the interceptor is called should be logged */
  val logAfter: Boolean = true,
)
