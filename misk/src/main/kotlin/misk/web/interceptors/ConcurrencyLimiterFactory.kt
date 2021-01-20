package misk.web.interceptors

import com.netflix.concurrency.limits.Limiter
import misk.Action

/**
 * Multibind an instance to provide a custom Limiter for concurrency shedding.
 * The first instance to return non-null is used.
 */
interface ConcurrencyLimiterFactory {
  fun create(action: Action): Limiter<String>?
}
