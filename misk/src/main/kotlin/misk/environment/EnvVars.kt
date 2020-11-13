package misk.environment

import misk.inject.KAbstractModule
import javax.inject.Inject
import javax.inject.Qualifier

/**
 * Loads an environment variable value.
 */
interface EnvVarLoader {
  /**
   * Get the environment variable value
   *
   * @throws IllegalStateException if the environment variable is not found
   */
  fun getEnvVar(name: String): String
}

/**
 * A Real [EnvVarLoader] that loads from the system environment variables.
 */
internal class RealEnvVarLoader @Inject constructor() : EnvVarLoader {
  override fun getEnvVar(name: String): String {
    return System.getenv(name) ?: throw IllegalStateException("$name env var not set")
  }
}

/**
 * Binds a [EnvVarLoader] for production
 */
class RealEnvVarModule : KAbstractModule() {
  override fun configure() {
    bind<EnvVarLoader>().to<RealEnvVarLoader>()
  }
}

@Qualifier
annotation class ForEnvVars

/**
 * A Fake [EnvVarLoader] that loads from an in memory map
 */
internal class FakeEnvVarLoader @Inject constructor(
  @ForEnvVars private val vars: Map<String, String>
) : EnvVarLoader {
  override fun getEnvVar(name: String): String {
    return vars[name] ?: throw IllegalStateException("$name env var not set")
  }
}

/**
 * Binds a [EnvVarLoader] for tests. Tests can contribute values through the
 * ```@ForEnvVars Map<String, String>``` binding.
 */
class FakeEnvVarModule : KAbstractModule() {
  override fun configure() {
    newMapBinder<String, String>(ForEnvVars::class)
    bind<EnvVarLoader>().to<FakeEnvVarLoader>()
  }
}
