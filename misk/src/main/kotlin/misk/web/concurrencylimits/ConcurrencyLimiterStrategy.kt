package misk.web.concurrencylimits

enum class ConcurrencyLimiterStrategy {
  VEGAS,
  GRADIENT,
  GRADIENT2,
  AIMD,
  SETTABLE,
  ;
}
