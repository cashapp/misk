package misk.environment

/**
 * The raw environment name. The set of possibilities is unbounded so this should rarely be used.
 * Instead, use the Deployment type for behavior based on characteristics of an environment.
 */
data class Env(val name: String)
