package misk.web.concurrencylimits

/**
 * Per the [Netflix library](https://github.com/Netflix/concurrency-limits#readme), strategies for
 * calculating concurrency limits based on existing traffic. For more information, please consult
 * the [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/package-summary.html).
 */
enum class ConcurrencyLimiterStrategy {
  /**
   * A limiter based on TCP Vegas where the limit increases by alpha if the queue_use is small
   * (< alpha) and decreases by alpha if the queue_use is large (> beta). See [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/VegasLimit.html)
   * for more information.
   */
  VEGAS,

  /**
   * Concurrency limit algorithm that adjust the limits based on the gradient of change in the
   * samples minimum RTT and absolute minimum RTT allowing for a queue of square root of the
   * current limit. See [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/GradientLimit.html)
   * for more information.
   */
  GRADIENT,

  /**
   * Concurrency limit algorithm that adjusts the limit based on the gradient of change of the
   * current average RTT and a long term exponentially smoothed average RTT. See [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/Gradient2Limit.html)
   * for more information.
   */
  GRADIENT2,

  /**
   * Loss based dynamic Limit that does an additive increment as long as there are no errors and a
   * multiplicative decrement when there is an error. See [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/AIMDLimit.html)
   * for more information.
   */
  AIMD,

  /**
   * Limit to be used mostly for testing where the limit can be manually adjusted. See [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/SettableLimit.html)
   * for more information.
   */
  SETTABLE,

  /**
   * Non dynamic limit with fixed value. See [documentation](https://javadoc.io/static/com.netflix.concurrency-limits/concurrency-limits-core/0.3.6/com/netflix/concurrency/limits/limit/FixedLimit.html)
   * for more information.
   */
  FIXED,
  ;
}
