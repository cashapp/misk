package misk.hibernate

interface Projection

/**
 * Annotates a parameter of a [Projection] data class to indicate which column (or path of columns)
 * to populate the parameter with.
 *
 * Properties may be created from an [aggregation] function, which will be applied to the column.
 * By default, no aggregation is applied.
 */
annotation class Property(
  val path: String,
  val aggregation: AggregationType = AggregationType.NONE
)
