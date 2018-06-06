package misk.hibernate

/** Marker interface for query projections. */
interface Projection {
}

/**
 * Annotates a parameter of a data class [Projection] to indicate which column (or path of
 * columns) to populate the parameter with.
 */
annotation class Property(
  val value: String
)
