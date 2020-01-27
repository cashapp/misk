package misk.hibernate.pagination

import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter

interface Pager<T> {
  /** Returns null when there are no more pages. */
  fun nextPage(session: Session): Page<T>?
}

/** Use a null [initialOffset] to start at the beginning. */
fun <T : DbEntity<T>, Q : Query<T>> Q.newPager(
  paginator: Paginator<T, Q>,
  initialOffset: Offset? = null,
  pageSize: Int = 100
): Pager<T> {
  return RealPager(this, paginator, initialOffset, pageSize)
}

fun <T : DbEntity<T>, R> Pager<T>.listAll(
  transacter: Transacter,
  transform: (T) -> R
): List<R> {
  val results = mutableListOf<R>()
  var offset: Offset?
  do {
    val nextPage = transacter.transaction { session -> nextPage(session) }
    val (pageContents, nextOffset) = nextPage ?: Page.empty()
    results.addAll(pageContents.map(transform))
    offset = nextOffset
  } while (offset != null)
  return results
}