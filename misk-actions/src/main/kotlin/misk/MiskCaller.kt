package misk

/** Information about the authenticated caller of a given action */
data class MiskCaller(
  /** Present if the caller is an authenticated peer service */
  val service: String? = null,

  /** Present if the caller is a human user, typically from an SSO proxy */
  val user: String? = null,

  /** Set of capabilities given to a human user, typically provided by the SSO infrastructure */
  val capabilities: Set<String> = setOf()
) {
  init {
    require(service != null || user != null) { "one of service or user is required" }
    require(service == null || user == null) { "only one of service or user is allowed" }
  }

  /** The identity of the calling principal, regardless of whether they are a service or a user */
  val principal: String get() = service ?: user!!

  /** We don't like to log usernames. */
  override fun toString(): String {
    return if (user != null) {
      "user=${user.firstOrNull() ?: ""}███████, capabilities=$capabilities"
    } else {
      "service=$service"
    }
  }

  /** Determine based on allowed capabilities/services if the caller is permitted */
  fun isAllowed(allowedCapabilities: Set<String>, allowedServices: Set<String>): Boolean {
    // Allow if we don't have any requirements on service or capability
    if (allowedServices.isEmpty() && allowedCapabilities.isEmpty()) return true

    // Allow if the caller has provided an allowed service
    if (service != null && allowedServices.contains(service)) return true

    // Allow if the caller has provided an allowed capability
    return capabilities.any { allowedCapabilities.contains(it) }
  }
}
