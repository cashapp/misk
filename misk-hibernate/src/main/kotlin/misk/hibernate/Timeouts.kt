package misk.hibernate

import java.time.Duration
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

data class Timeouts internal constructor(val warnAfter: Duration, val killAfter: Duration) {
  fun warnTimeoutEnabled(): Boolean = warnAfter.isNatural()
  fun killTimeoutEnabled(): Boolean = killAfter.isNatural()

  companion object {
    /** Zero valued timeouts. */
    val NONE = Timeouts(Duration.ZERO, Duration.ZERO)

    fun create(warnAfter: Duration): Timeouts {
      require(warnAfter.isNatural()) {
        "warnAfter must be a positive non-zero duration"
      }
      return Timeouts(warnAfter, Duration.ZERO)
    }

    fun create(warnAfter: Duration, killAfter: Duration): Timeouts {
      require(warnAfter.isNatural()) {
        "warnAfter must be a positive non-zero duration"
      }
      require(killAfter.isNatural()) {
        "killAfter must be a positive non-zero duration"
      }
      return Timeouts(warnAfter, killAfter)
    }

    private fun Duration.isNatural(): Boolean = !this.isNegative && !this.isZero
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class TransactionTimeouts

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class QueryTimeouts

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class SlowQueryTimeouts

@Singleton
/** Timeouts policies. Setting [Timeouts.NONE] disables the field's timeout policy. */
data class TimeoutsConfig @Inject constructor(
  @TransactionTimeouts val transaction: Timeouts,
  @QueryTimeouts val query: Timeouts,
  @SlowQueryTimeouts val slowQuery: Timeouts
)