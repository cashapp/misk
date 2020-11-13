package misk.hibernate.pagination

import misk.hibernate.Query

/**
 * Breaks a query into pages. Each page break is delimited by a string that is the offset of the
 * last row of the preceding page.
 */
interface Paginator<T, in Q : Query<T>> {
  /** Figure out what the offset of [row] is, and encode that as a string. */
  fun getOffset(row: T): Offset

  /**
   * Adjust [query] so that is in paging order and offset by [offset] (if non-null).
   * Most implementations will add an `ORDER BY` clause. They should also add
   * a `WHERE column > offset` constraint if the offset is non-null.
   */
  fun applyOffset(query: Q, offset: Offset?)
}
