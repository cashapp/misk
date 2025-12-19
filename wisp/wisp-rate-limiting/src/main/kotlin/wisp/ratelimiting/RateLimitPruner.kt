package wisp.ratelimiting

/** A standard interface for pruning expired rate limits */
interface RateLimitPruner {
  fun prune()
}
