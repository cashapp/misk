package misk.jooq.listeners

import misk.jooq.toLocalDateTime
import org.jooq.RecordContext
import org.jooq.impl.DSL
import org.jooq.impl.DefaultRecordListener
import java.time.Clock

/**
 * A Record Listener that will automatically set the current timestamp for the [createdAtColumnName]
 * during insertions. And the current timestamp to the [updatedAtColumnName] while updating a row
 */
class JooqTimestampRecordListener(
  private val clock: Clock,
  private val createdAtColumnName: String,
  private val updatedAtColumnName: String,
) : DefaultRecordListener() {

  override fun updateStart(ctx: RecordContext?) {
    if (ctx?.record()?.field(updatedAtColumnName) != null) {
      ctx.record().set(DSL.field(updatedAtColumnName), clock.instant().toLocalDateTime())
    }
  }

  override fun insertStart(ctx: RecordContext?) {
    if (ctx?.record()?.field(createdAtColumnName) != null) {
      ctx.record().set(DSL.field(createdAtColumnName), clock.instant().toLocalDateTime())
    }
    if (ctx?.record()?.field(updatedAtColumnName) != null) {
      ctx.record().set(DSL.field(updatedAtColumnName), clock.instant().toLocalDateTime())
    }
  }
}


/**
 * Use this class to configure the installation of the
 * [misk.jooq.listeners.JooqTimestampRecordListener]
 * You can use both or configure just one of the [createdAtColumnName] [updatedAtColumnName] to be
 * set to the current timestamp when inserting or updating it.
 * If you leave the one you don't want set as an empty string the [JooqTimestampRecordListener] will
 * ignore it.
 */
data class JooqTimestampRecordListenerOptions(
  val install: Boolean,
  val createdAtColumnName: String = "",
  val updatedAtColumnName: String = ""
)
