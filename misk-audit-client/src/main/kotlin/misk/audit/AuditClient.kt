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

/**
 * Annotation indicating that request and response information should be sent to the audit client.
 *
 * If you would like to turn off logging for all non-successful requests, use the parameter [successOnly].
 * otherwise, all requests will be sent to the audit client including failures.
 *
 * If arguments and responses may include sensitive information, it is expected that the toString()
 * methods of these objects will redact it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class AuditRequestResponse(
  /** The specific resource the event relates to. */
  val target: String = "",
  /** Human-readable description of the event, limited to 4000 characters. Optional, but suggested even if richDescription is provided. */
  val description: String = "",
  /** Whether this change is being done directly by a human (false) or by an automated system (true) */
  val automatedChange: Boolean = false,
  /** Rich text description of the event, limited to 4000 characters. Optional if description is provided. */
  val richDescription: String = "",
  /** URL to a page with more details about the event. */
  val detailURL: String = "",
  /** Name of the application (slug) if different from the eventSource, otherwise null. */
  val applicationName: String = "",
  /** If false, request body will not be included. */
  val includeRequest: Boolean = false,
  /** If false, response body will not be included. */
  val includeResponse: Boolean = false,
  /** If false, request headers will not be included. */
  val includeRequestHeaders: Boolean = false,
  /** If false, response headers will not be included. */
  val includeReseponseHeaders: Boolean = false,
)
