package misk.jooq

import org.jooq.Record
import org.jooq.ResultQuery
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

fun <RECORD : Record?> ResultQuery<RECORD>.fetchOneOrNull(): RECORD? =
  this.fetchOptional().orElse(null)

fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
fun LocalDateTime.toInstant(): Instant = this.toInstant(ZoneOffset.UTC)

fun <ANY> ANY?.getOrThrow(): ANY = this
  ?: throw IllegalStateException("Expecting value to not be null")
