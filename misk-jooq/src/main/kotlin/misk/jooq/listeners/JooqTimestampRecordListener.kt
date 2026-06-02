package misk.jooq.listeners

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import misk.jooq.toLocalDateTime
import org.jooq.RecordContext
import org.jooq.RecordListener
import org.jooq.impl.DSL

/**
 * A Record Listener that will automatically set the current timestamp for the [createdAtColumnName] during insertions.
 * And the current timestamp to the [updatedAtColumnName] while updating a row
 */
class JooqTimestampRecordListener(
  private val clock: Clock,
  private val createdAtColumnName: String,
  private val updatedAtColumnName: String,
) : RecordListener {

  override fun updateStart(ctx: RecordContext?) {
    setTime(ctx, updatedAtColumnName)
  }

  override fun insertStart(ctx: RecordContext?) {
    setTime(ctx, createdAtColumnName)
    setTime(ctx, updatedAtColumnName)
  }

  private fun setTime(ctx: RecordContext?, columnName: String) {
    if (
      ctx?.record()?.field(columnName) != null &&
        (ctx.record().get(DSL.field(columnName)) == null || !ctx.record().changed(DSL.field(columnName)))
    ) {
      val precision = ctx.recordType().field(DSL.field(columnName))!!.dataType.precision()
      ctx.record().set(DSL.field(columnName), clock.instant().truncateBasedOnPrecision(precision))
    }
  }

  /**
   * MySQL's precision for a timestamp is millis. But in the Kube Pod, where the code runs the JVM timestamp is in
   * nanos. So when we store the data, the signature is computed with nanos, but when we load the data from the DB, the
   * nanos are lost. It is best we keep the same precision when we store and load the data. This method truncates the
   * instant based on the precision. The check with precision is required to be able to test this on a MAC. Mac JVM's
   * precision is millis. So in order to test truncation we need to create a mysql timestamp with a precision of 0. This
   * also allows this signature to work for any column created in prod where the precision is 0 (in the sense,
   * restricted to store seconds alone).
   */
  private fun Instant.truncateBasedOnPrecision(precision: Int): LocalDateTime {
    return when {
      precision < 3 -> truncatedTo(ChronoUnit.SECONDS)
      else -> truncatedTo(ChronoUnit.MILLIS)
    }.toLocalDateTime()
  }
}

/**
 * Use this class to configure the installation of the [misk.jooq.listeners.JooqTimestampRecordListener] You can use
 * both or configure just one of the [createdAtColumnName] [updatedAtColumnName] to be set to the current timestamp when
 * inserting or updating it. If you leave the one you don't want set as an empty string the
 * [JooqTimestampRecordListener] will ignore it.
 */
data class JooqTimestampRecordListenerOptions
@JvmOverloads
constructor(val install: Boolean, val createdAtColumnName: String = "", val updatedAtColumnName: String = "")
