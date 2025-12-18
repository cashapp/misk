package misk.lease.mysql

import app.cash.sqldelight.ColumnAdapter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal object InstantAdapter : ColumnAdapter<Instant, OffsetDateTime> {
  override fun encode(value: Instant) = value.atOffset(ZoneOffset.UTC)

  override fun decode(databaseValue: OffsetDateTime): Instant = databaseValue.toInstant()
}
