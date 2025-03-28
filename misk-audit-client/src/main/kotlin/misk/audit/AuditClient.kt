package misk.audit

/**
 * Exposes a simple interface to log events from various services or platforms to a single audit data store.
 */
interface AuditClient {
  fun logEvent(
    /** The specific resource the event relates to. */
    target: String,
    /** Human-readable description of the event, limited to 4000 characters. Optional, but suggested even if richDescription is provided. */
    description: String,
    /** Whether this change is being done directly by a human (false) or by an automated system (true) */
    automatedChange: Boolean = false,
    /** Rich text description of the event, limited to 4000 characters. Optional if description is provided. */
    richDescription: String? = null,
    /** URL to a page with more details about the event. */
    detailURL: String? = null,
    /** LDAP of the approver (optional, for dual-auth). */
    approverLDAP: String? = null,
    /** LDAP of the requestor, should be provided when automatedChange is false, optional otherwise, leave null unless different from current caller */
    requestorLDAP: String? = null,
    /** Name of the application (slug) if different from the eventSource, otherwise null. */
    applicationName: String? = null,
  )
}
