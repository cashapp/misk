package com.squareup.library.persistence

import com.squareup.library.db.Books
import com.squareup.sqldelight.ColumnAdapter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder

val timestampInstantAdapter = object : ColumnAdapter<Instant, String> {
  private val dateTimeFormat = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss")
    .toFormatter()
    .withZone(ZoneId.of("UTC"))

  override fun decode(databaseValue: String) = dateTimeFormat.parse(databaseValue, Instant::from)
  override fun encode(value: Instant) = dateTimeFormat.format(value)
}

val booksAdapter = Books.Adapter(
  idAdapter = BookId.Adapter,
  created_atAdapter = timestampInstantAdapter,
  updated_atAdapter = timestampInstantAdapter,
)
