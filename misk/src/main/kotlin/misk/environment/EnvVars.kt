package misk.environment

import misk.inject.KAbstractModule
import wisp.deployment.EnvironmentVariableLoader
import wisp.deployment.FakeEnvironmentVariableLoader
import wisp.deployment.RealEnvironmentVariableLoader
import javax.inject.Inject
import javax.inject.Qualifier

/*
 * Soft deprecating for wisp.deployment.EnvironmentVariableLoader
 */

/**
 * Loads an environment variable value.
 */
interface EnvVarLoader : EnvironmentVariableLoader {
  /**
   * Get the environment variable value
   *
   * @throws IllegalStateException if the environment variable is not found
   */
  fun getEnvVar(name: String): String = getEnvironmentVariable(name)
}

/**
 * A Real [EnvVarLoader] that loads from the system environment variables.
 *
 * This will be replaced with [RealEnvironmentVariableLoader] in the future
 */
internal class RealEnvVarLoader @Inject constructor() : EnvVarLoader {
  val delegate = RealEnvironmentVariableLoader()

  override fun getEnvVar(name: String): String = getEnvironmentVariable(name)

  override fun getEnvironmentVariable(name: String): String =
    delegate.getEnvironmentVariable(name)

  override fun getEnvironmentVariableOrDefault(name: String, defaultValue: String): String =
    delegate.getEnvironmentVariableOrDefault(name, defaultValue)
}

/**
 * Binds a [EnvVarLoader] for production
 */
class RealEnvVarModule : KAbstractModule() {
  override fun configure() {
    bind<EnvVarLoader>().to<RealEnvVarLoader>()  // remove when RealEnvVarLoader is eradicated
    bind<EnvironmentVariableLoader>().to<RealEnvVarLoader>()
  }
}

@Qualifier
annotation class ForEnvVars

/**
 * A Fake [EnvVarLoader] that delegates to [FakeEnvironmentVariableLoader] providing an
 * injected memory map of environment variables annotated with [ForEnvVars].
 */
internal class FakeEnvVarLoader @Inject constructor(
  @ForEnvVars private val vars: Map<String, String>
) : EnvVarLoader {

  val delegate = FakeEnvironmentVariableLoader(vars.toMutableMap())

  override fun getEnvVar(name: String): String = getEnvironmentVariable(name)

  override fun getEnvironmentVariable(name: String): String =
    delegate.getEnvironmentVariable(name)

  override fun getEnvironmentVariableOrDefault(name: String, defaultValue: String): String =
    delegate.getEnvironmentVariableOrDefault(name, defaultValue)
}

/**
 * Binds a [EnvVarLoader] for tests. Tests can contribute values through the
 * ```@ForEnvVars Map<String, String>``` binding.
 */
class FakeEnvVarModule : KAbstractModule() {
  override fun configure() {
    newMapBinder<String, String>(ForEnvVars::class)
    bind<EnvVarLoader>().to<FakeEnvVarLoader>() // remove when FakeEnvVarLoader is eradicated
    bind<EnvironmentVariableLoader>().to<FakeEnvVarLoader>()
  }
}
