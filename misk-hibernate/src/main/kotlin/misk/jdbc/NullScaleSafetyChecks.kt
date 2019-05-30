package misk.jdbc

class NullScaleSafetyChecks : ScaleSafetyChecks {
  override fun <T> disable(body: () -> T): T {
    return body()
  }
}