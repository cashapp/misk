package wisp.deployment

/**
 * Loads an environment variable value.
 */
interface EnvironmentVariableLoader {
    /**
     * Get the environment variable value that must exist
     *
     * @throws IllegalStateException if the environment variable is not found
     */
    fun getEnvironmentVariable(name: String): String =
        System.getenv(name) ?: throw IllegalStateException("$name environment variable not set")

    /**
     * Get the environment variable value, or if it does not exist, use the
     * [defaultValue] supplied.
     */
    fun getEnvironmentVariableOrDefault(name: String, defaultValue: String): String =
        System.getenv(name) ?: defaultValue

    companion object {
        val real = RealEnvironmentVariableLoader()
    }
}

class RealEnvironmentVariableLoader : EnvironmentVariableLoader
