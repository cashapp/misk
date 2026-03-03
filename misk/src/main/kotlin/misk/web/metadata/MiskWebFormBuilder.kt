package misk.web.metadata

/** Akin to a Proto Message, a Type has a list of fields */
data class Type(val fields: List<Field>)

/**
 * Akin to a Proto field, a field can be of primitive or another Message type,
 * and can be repeated to become a list
 *
 * Enums are encoded to contain their values within their Type definition as opposed to a unique Type
 */
data class Field(val name: String, val type: String, val repeated: Boolean)