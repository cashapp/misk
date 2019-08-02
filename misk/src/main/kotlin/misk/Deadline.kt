package misk

import misk.exceptions.BadRequestException
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.web.HttpCall
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Deadline for an action. A deadline can be overridden, with the caveat that the overridden deadline
 * does not propagate to other threads.
 *
 * All actions have [ActionDeadline]s available even if there is initially no deadline.
 */
class ActionDeadline(
  private val clock: Clock,
  deadline: Instant?
) {

  constructor(clock: Clock, duration: Duration) : this(clock, clock.instant().plus(duration))

  private val threadDeadline: ThreadLocal<Instant?> = ThreadLocal.withInitial { deadline }

  /**
   * Get the current deadline, or null if there is none.
   *
   * If the deadline is currently being overridden in the same thread, that value is returned.
   */
  fun current(): Instant? = threadDeadline.get()

  /** @return If the deadline is expired. If there is no deadline this returns false */
  fun expired(): Boolean = current()?.let { it < clock.instant() } ?: false

  /** @return Get the duration until the deadline is hit, or null if there is no deadline */
  fun remaining(): Duration? = current()?.let { deadline ->
    val left = Duration.between(clock.instant(), deadline)
    if (left.isNegative) Duration.ZERO else left
  }

  /**
   * Override the deadline for the given block. The overridden deadline only applies to the current
   * thread.
   *
   * The lesser of the given deadline and current deadline is used.
   */
  fun <T> overriding(newDeadline: Instant, fn: () -> T): T {
    val current = threadDeadline.get()
    if (current != null && newDeadline >= current) {
      return fn()
    }

    threadDeadline.set(newDeadline)
    return try {
      fn()
    } finally {
      threadDeadline.set(current)
    }
  }

  /**
   * Override the deadline for the given block. The overridden deadline only applies to the current
   * thread.
   *
   * The lesser of the given deadline and current deadline is used.
   */
  fun <T> overriding(duration: Duration, fn: () -> T): T {
    check(!duration.isZero && !duration.isNegative) { "duration must be positive" }
    return overriding(clock.instant().plus(duration), fn)
  }
}

/**
 * Provides an optional deadline scoped to the current action, determined by the optional
 * "X-Request-Timeout-Ms" HTTP header.
 */
class ActionDeadlineProvider @Inject constructor(
  private val clock: Clock,
  var currentRequest: ActionScoped<HttpCall>
) : ActionScopedProvider<ActionDeadline> {
  override fun get(): ActionDeadline {
    val deadline = currentRequest.get().requestHeaders[HTTP_HEADER]?.let {
      try {
        val timeoutMs = it.toLong()
        if (timeoutMs <= 0) {
          throw BadRequestException(
              "Invalid header value for $HTTP_HEADER; deadline must be positive")
        }
        clock.instant().plusMillis(timeoutMs)
      } catch (ex: NumberFormatException) {
        throw BadRequestException("Invalid header value for $HTTP_HEADER")
      }
    }

    return ActionDeadline(clock, deadline)
  }

  companion object {
    const val HTTP_HEADER = "X-Request-Timeout-Ms"
  }
}
