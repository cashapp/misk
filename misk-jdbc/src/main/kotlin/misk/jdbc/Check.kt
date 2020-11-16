package misk.jdbc

import java.util.EnumSet

enum class Check {
  FULL_SCATTER,
  TABLE_SCAN,
  COWRITE
}

object CheckDisabler {
  val disabledChecks: ThreadLocal<Collection<Check>> = ThreadLocal.withInitial { listOf() }

  fun isCheckEnabled(check: Check): Boolean {
    return !disabledChecks.get().contains(check)
  }

  fun <T> withoutChecks(vararg checks: Check, body: () -> T): T {
    val previous = disabledChecks.get()
    val actualChecks = if (checks.isEmpty()) {
      EnumSet.allOf(Check::class.java)
    } else {
      EnumSet.of(checks[0], *checks)
    }
    disabledChecks.set(actualChecks)
    return try {
      body()
    } finally {
      disabledChecks.set(previous)
    }
  }

  fun <T> disableChecks(checks: Collection<Check>, body: () -> T): T {
    val previous = disabledChecks.get()
    disabledChecks.set(previous + checks)
    return try {
      body()
    } finally {
      disabledChecks.set(previous)
    }
  }
}