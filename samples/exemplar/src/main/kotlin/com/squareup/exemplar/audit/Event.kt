package com.squareup.exemplar.audit

import com.squareup.moshi.Json

/**
 * Change event schema for Squawk service
 *
 * @param eventSource What generated the event, likely an app or tool name. E.g. \"keywhiz\", \"cloud-cd\",
 *   \"squaremason\", \"nodeterminator\", etc.
 * @param eventTarget the specific resource the event relates to
 * @param timestampSent Unix timestamp in nanoseconds when event was sent
 * @param applicationName Name of the application (slug)
 * @param applicationTier Application reliability tier
 * @param approverLDAP LDAP of the approver (optional, for dual-auth)
 * @param automatedChange Whether this change is being done directly by a human (false) or by an automated system (true)
 * @param description Human-readable description of the event, limited to 4000 characters. Optional, but suggested even
 *   if richDescription is provided.
 * @param richDescription Rich text description of the event, limited to 4000 characters. Optional if description is
 *   provided.
 * @param environment Environment where the event occurred
 * @param detailURL URL to a page with more details about the event
 * @param region Cloud region (AWS or otherwise) or datacenter locality
 * @param requestorLDAP LDAP of the requestor, should be provided when automatedChange is false, optional otherwise
 */

// Set as internal to prevent use outside of client and strict dependence on the API, which may change
internal data class Event(
  /**
   * What generated the event, likely an app or tool name. E.g. \"keywhiz\", \"cloud-cd\", \"squaremason\",
   * \"nodeterminator\", etc.
   */
  @Json(name = "eventSource") val eventSource: String,

  /** the specific resource the event relates to */
  @Json(name = "eventTarget") val eventTarget: String,

  /** Unix timestamp in nanoseconds when event was sent */
  @Json(name = "timestampSent") val timestampSent: Int,

  /** Name of the application (slug) */
  @Json(name = "applicationName") val applicationName: String? = null,

  /** Application reliability tier */
  @Json(name = "applicationTier") val applicationTier: String? = null,

  /** LDAP of the approver (optional, for dual-auth) */
  @Json(name = "approverLDAP") val approverLDAP: String? = null,

  /** Whether this change is being done directly by a human (false) or by an automated system (true) */
  @Json(name = "automatedChange") val automatedChange: Boolean? = false,

  /**
   * Human-readable description of the event, limited to 4000 characters. Optional, but suggested even if
   * richDescription is provided.
   */
  @Json(name = "description") val description: String? = null,

  /** Rich text description of the event, limited to 4000 characters. Optional if description is provided. */
  @Json(name = "richDescription") val richDescription: String? = null,

  /** Environment where the event occurred */
  @Json(name = "environment") val environment: String? = null,

  /** URL to a page with more details about the event */
  @Json(name = "detailURL") val detailURL: String? = null,

  /** Cloud region (AWS or otherwise) or datacenter locality */
  @Json(name = "region") val region: String? = null,

  /** LDAP of the requestor, should be provided when automatedChange is false, optional otherwise */
  @Json(name = "requestorLDAP") val requestorLDAP: String? = null,
)
