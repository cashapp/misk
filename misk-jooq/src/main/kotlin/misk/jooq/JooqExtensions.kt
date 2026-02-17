package misk.jooq

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.jooq.Condition
import org.jooq.impl.DSL

fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)

inline fun <ANY> ANY?.ifNotNull(condition: (any: ANY) -> Condition): Condition =
  this?.let { condition(it) } ?: DSL.noCondition()
