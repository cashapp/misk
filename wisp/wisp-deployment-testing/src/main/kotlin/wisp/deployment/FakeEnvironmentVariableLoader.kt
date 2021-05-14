package wisp.deployment

/**
 * A Fake [EnvironmentVariableLoader] that loads from an in memory map
 */
class FakeEnvironmentVariableLoader(val vars: Map<String, String>) : EnvironmentVariableLoader {
  override fun getEnvironmentVariable(name: String): String {
    return vars[name] ?: throw IllegalStateException("$name environment variable not set")
  }

  override fun getEnvironmentVariableOrDefault(name: String, defaultValue: String): String {
    return vars[name] ?: defaultValue
  }
}
