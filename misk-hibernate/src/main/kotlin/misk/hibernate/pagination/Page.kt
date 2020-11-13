package misk.hibernate.pagination

data class Page<T>(
  val contents: List<T>,

  /** Null if there are no more results. Pass this in a subsequent call to [Query.newPager]. */
  val nextOffset: Offset?
) {
  companion object {
    fun <T> empty(): Page<T> {
      return Page(emptyList(), null)
    }
  }
}
