package wisp.ratelimiting

enum class RateLimitBucketRefillStrategy {
  /*
   * The bucket will be filled continuously at the specified rate
   */
  GREEDY,
  /*
   * The bucket will be topped off at the end of the interval,
   * no matter when the last token was consumed.
   */
  INTERVAL
}
