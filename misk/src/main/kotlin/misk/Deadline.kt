package misk

import misk.exceptions.BadRequestException
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.web.HttpCall
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class Deadline(
  private val clock: Clock,
  val deadline: Instant
) {

  constructor(clock: Clock, duration: Duration) :
      this(clock, clock.instant().plusNanos(duration.toNanos()))

  fun expired() = !clock.instant().isBefore(deadline)

  fun remaining(): Duration {
    val left = Duration.between(clock.instant(), deadline)
    return if (left.isNegative) Duration.ZERO else left
  }
}

/**
 * Provides an optional deadline scoped to the current action, determined by the optional
 * "X-Request-Timeout-Ms" HTTP header.
 */
class DeadlineProvider @Inject constructor(
  private val clock: Clock,
  var currentRequest: ActionScoped<HttpCall>
) : ActionScopedProvider<Deadline?> {
  override fun get(): Deadline? = currentRequest.get().requestHeaders[HTTP_HEADER]?.let {
    try {
      val duration = Duration.ofMillis(it.toLong())
      if (duration.isNegative || duration.isZero) {
        throw BadRequestException(
            "Invalid header value for $HTTP_HEADER; deadline must be positive")
      }
      Deadline(clock, duration)
    } catch (ex: NumberFormatException) {
      throw BadRequestException("Invalid header value for $HTTP_HEADER")
    }
  }

  companion object {
    const val HTTP_HEADER = "X-Request-Timeout-Ms"
  }
}
