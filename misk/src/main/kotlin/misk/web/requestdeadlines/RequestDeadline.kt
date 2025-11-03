package misk.web.requestdeadlines

import java.time.Clock
import java.time.Duration
import java.time.Instant

data class RequestDeadline(
  val clock: Clock,
  val deadline: Instant?
) {
  fun expired(): Boolean {
    return deadline?.let { clock.instant().isAfter(deadline) } ?: false
  }

  fun remaining(): Duration? {
    return deadline?.let { deadline ->
      val timeLeft = Duration.between(clock.instant(), deadline)
      if (timeLeft.isNegative) Duration.ZERO else timeLeft
    }
  }

  fun expiredDuration(): Duration {
    return if (expired()) {
      deadline?.let { Duration.between(it, clock.instant()) } ?: Duration.ZERO
    } else {
      Duration.ZERO
    }
  }
}
