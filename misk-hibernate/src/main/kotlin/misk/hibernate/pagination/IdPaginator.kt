package misk.hibernate.pagination

import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Query

/** Pages through entities by ascending ID. */
fun <T : DbEntity<T>> idAscPaginator(idPath: String = "id"): Paginator<T, Query<T>> {
  return IdPaginator(idPath, Operator.GT)
}

/** Pages through entities by descending ID. */
fun <T : DbEntity<T>> idDescPaginator(idPath: String = "id"): Paginator<T, Query<T>> {
  return IdPaginator(idPath, Operator.LT)
}

internal class IdPaginator<T : DbEntity<T>>(
  private val idPath: String,
  private val operator: Operator
) : Paginator<T, Query<T>> {

  init {
    require(operator == Operator.GT || operator == Operator.LT)
  }

  override fun getOffset(row: T): Offset {
    return encodeOffset(row.id)
  }

  override fun applyOffset(query: Query<T>, offset: Offset?) {
    query.dynamicAddOrder(idPath, asc = operator == Operator.GT)
    if (offset == null) {
      return
    }
    query.dynamicAddConstraint(idPath, operator, decodeOffset(offset))
  }

  private fun encodeOffset(id: Id<T>): Offset {
    return Offset(id.toString())
  }

  private fun decodeOffset(offset: Offset): Id<T> {
    return Id(offset.offset.toLong())
  }
}
