package misk.jdbc

interface ScaleSafetyChecks {
  fun <T> disable(body: () -> T): T
}
