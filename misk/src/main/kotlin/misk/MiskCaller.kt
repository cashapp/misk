package misk

/** Information about the authenticated caller of a given action */
data class MiskCaller(
  /** Present if the caller is an authenticated peer service */
  val service: String? = null,

  /** Present if the caller is a human user, typically from an SSO proxy */
  val user: String? = null,

  /** Set of roles to which the human user belongs, typically provided by the SSO infrastructure */
  @Deprecated("use capabilities instead. https://github.com/cashapp/misk/issues/1078", replaceWith = ReplaceWith("capabilities"))
  val roles: Set<String> = setOf(),

  /** Set of capabilities given to a human user, typically provided by the SSO infrastructure */
  val capabilities: Set<String> = setOf()
) {
  init {
    require(service != null || user != null) { "one of service or user is required" }
    require(service == null || user == null) { "only one of service or user is allowed" }
  }

  /** The identity of the calling principal, regardless of whether they are a service or a user */
  val principal: String get() = service ?: user!!

  /** A concat of roles and capabilities to aid in the transition from roles to capabilities */
  val allCapabilities = roles + capabilities
}
