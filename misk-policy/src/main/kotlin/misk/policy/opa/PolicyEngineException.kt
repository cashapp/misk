package misk.policy.opa

class PolicyEngineException(
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)
