package misk.hibernate.pagination

import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.hibernate.Session

internal class RealPager<T : DbEntity<T>, Q: Query<T>>(
  private val query: Q,
  private val paginator: Paginator<T, Q>,
  initialOffset: Offset? = null,
  private val pageSize: Int
) : Pager<T> {

  private var nextOffset: Offset? = initialOffset

  init {
    check(pageSize > 0)
  }

  override fun nextPage(session: Session): Page<T>? {
    paginator.applyOffset(query, nextOffset)
    val (contents, hasMore) = query.listWithHasMore(session, pageSize)
    nextOffset = if (hasMore) {
      contents.lastOrNull()?.let(paginator::getOffset)
    } else {
      null
    }
    return Page(contents, nextOffset)
  }

  private fun Query<T>.listWithHasMore(session: Session, pageSize: Int): Pair<List<T>, Boolean> {
    // Request an extra element.
    maxRows = pageSize + 1
    val contents = list(session)
    return if (contents.size == maxRows) {
      // We have an extra element, which means there's a next page!
      contents.subList(0, maxRows - 1) to true
    } else {
      contents to false
    }
  }
}