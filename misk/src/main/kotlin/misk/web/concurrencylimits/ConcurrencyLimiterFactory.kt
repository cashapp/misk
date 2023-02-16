package misk.web.concurrencylimits

import com.netflix.concurrency.limits.Limiter
import misk.Action

/**
 * Multibind an instance to provide a custom Limiter for concurrency shedding.
 * The first instance to return non-null is used.
 *
 * Misk's ConcurrencyLimitsInterceptor honors the `Quota-Path` HTTP header to give callers control
 * of how their calls are aggregated when computing system throughput. The [create] function will be
 * called for each unique Quota-Path received from an application. If the same Quota-Path header is
 * used on different actions, [create] is only called for the first action that uses the header.
 */
interface ConcurrencyLimiterFactory {
  fun create(action: Action): Limiter<String>?
}
