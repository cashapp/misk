package misk.hibernate.pagination

/**
 * Offset into a query. Typically this is a string-encoded ID like 5, or a composite containing a
 * date followed by an ID, like "2019-08-16T:10:26:51Z/5".
 *
 * Don't put PII in here. Clients see these in URLs and API calls, and can manipulate them. This
 * could also potentially leak database growth information!
 */
data class Offset(
  val offset: String
)

