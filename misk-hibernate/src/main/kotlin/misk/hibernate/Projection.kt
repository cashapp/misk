package misk.hibernate

/**
 * Marker interface for query projections.
 *
 * Projections are used to define the shape of the result set of a query, often as a subset of the properties of the
 * entity or entities being queried.
 *
 * For example, if we have a `DbMovie` entity with a `name`, `release_date`, and other properties we could use a
 * projection to only select the `name` and `release_date` properties:
 * ```
 * data class NameAndReleaseDate(
 *  @Property("name") var name: String,
 *  @Property("release_date") var releaseDate: LocalDate?
 * ) : Projection
 * ```
 */
interface Projection

/**
 * Annotates a parameter of a [Projection] data class to indicate which column (or path of columns) to populate the
 * parameter with.
 *
 * Properties may be created from an [aggregation] function, which will be applied to the column. By default, no
 * aggregation is applied.
 */
annotation class Property(val path: String, val aggregation: AggregationType = AggregationType.NONE)
