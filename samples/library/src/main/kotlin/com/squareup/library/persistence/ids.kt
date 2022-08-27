package com.squareup.library.persistence

import com.squareup.sqldelight.ColumnAdapter

/** Typed class to be used instead of raw Longs wherever row Id is used */
data class BookId(private val id: Long) {
  object Adapter : ColumnAdapter<BookId, Long> {
    override fun decode(databaseValue: Long) = BookId(databaseValue)
    override fun encode(value: BookId) = value.id
  }
}
