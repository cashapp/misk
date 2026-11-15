package misk.policy.opa

class PolicyEngineException @JvmOverloads constructor(message: String, cause: Throwable? = null) :
  Exception(message, cause)
