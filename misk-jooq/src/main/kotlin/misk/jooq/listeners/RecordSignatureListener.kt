package misk.jooq.listeners

import misk.jooq.toInstant
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.jooq.Record
import org.jooq.RecordContext
import org.jooq.Table
import org.jooq.TableField
import org.jooq.exception.DataAccessException
import misk.logging.getLogger
import org.jooq.RecordListener

/**
 * Using this listener will allow you to guard against direct DB updates. This listener will compute a MAC signature
 * from the column values provided and store the mac into another column in the DB. When it comes time to retrieve the
 * record, the mac will be verified with column data again. If the column data has been changed, then the mac will not
 * validate, and you will get an exception.
 *
 * Things to remember: Add this in when your service is slightly mature. You can't arbitrarily change the columns used
 * nor change the column order in creating the mac later on. This will prevent all old rows from being read.
 *
 * You can add this listener behind a flag and correct the signatures using a backfill and then use this listener again
 *
 * Also, general Jooq RecordListener rules apply. Specifically - As such, a RecordListener does not affect any bulk DML
 * statements (e.g. a DSLContext.update(Table)), whose affected records are not available to clients more info here
 * [org.jooq.RecordListener]
 *
 * Important! If you use the [JooqTimestampRecordListener] make sure this listener is added after the
 * [RecordSignatureListener] if you are using created_at and updated_at columns in the signature. So something like this
 *
 * To see a more the full example see the [RecordSignatureListenerTest]
 */
class RecordSignatureListener(
  private val recordHasher: RecordHasher,
  private val tableSignatureDetails: List<TableSignatureDetails>,
) : RecordListener {

  /**
   * We are overriding insertStart and updateStart instead of storeStart() which is called regardless of whether it is
   * an update or an insert. The reason is, people generally add another listener - JooqTimestampRecordListener which
   * sets the timestamp for created_at and updated_at columns. If these columns form part of the signature then these
   * values need to be set before the signature can be calculated. The JooqTimestampRecordListener has insertStart and
   * updateStart overridden. Jooq looks to be calling storeStart() on all listeners before moving on to call
   * insertStart() and updateStart() on all listeners. So either all listeners need to implement storeStart() or
   * insertStart() and updateStart(). We can mix the 2 styles and guarantee an order.
   */
  override fun insertStart(ctx: RecordContext?) = updateSignature(ctx)

  override fun updateStart(ctx: RecordContext?) = updateSignature(ctx)

  private fun updateSignature(ctx: RecordContext?) {
    if (ctx?.record() == null) return
    val tableSignature = tableSignatureDetails.find { ctx.record().field(it.signatureRecordColumn) != null } ?: return

    val concatenatedByteArray = concatenateByteArrayFromColumnValues(tableSignature, ctx)
    val signature = recordHasher.computeMac(tableSignature.signatureKeyName, concatenatedByteArray)
    ctx.record().set(tableSignature.signatureRecordColumn, signature)
  }

  override fun loadEnd(ctx: RecordContext?) {
    if (ctx?.record() == null) return
    val tableSignature = tableSignatureDetails.find { ctx.record().field(it.signatureRecordColumn) != null } ?: return

    // Skip validation if all signature columns are null (indicates a partially loaded record)
    val allColumnsNull = tableSignature.columns.all { column ->
      ctx.record().get(column) == null
    }
    if (allColumnsNull) return

    val concatenatedByteArray = concatenateByteArrayFromColumnValues(tableSignature, ctx)
    val signature =
      ctx.record().get(tableSignature.signatureRecordColumn)
        ?: if (!tableSignature.allowNullSignatures) {
          throw DataIntegrityException(exceptionMessage("Signature is null", tableSignature, ctx))
        } else {
          return
        }

    try {
      recordHasher.verifyMac(tableSignature.signatureKeyName, signature, concatenatedByteArray)
    } catch (e: Exception) {
      log.warn(e) {
        exceptionMessage("The data in the database does not match the record signature on the", tableSignature, ctx)
      }

      throw DataIntegrityException(
        exceptionMessage("The data in the database does not match the record signature on the", tableSignature, ctx),
        cause = e,
      )
    }
  }

  private fun concatenateByteArrayFromColumnValues(
    tableSignature: TableSignatureDetails,
    ctx: RecordContext,
  ): ByteArray {
    /**
    Here, we have implemented LV (length-value) encoding scheme.
    Encoding the column values and concatenating them as byte array in this manner
    prevents these two distinct records creating the same signature,
    given that signature is built using values from foo and bar columns.

    Encoding scheme:
    - null: 4 bytes with value -1 (no data follows)
    - non-null: 4 bytes with length >= 0, followed by that many bytes of data

    more info here: https://en.wikipedia.org/wiki/Type%E2%80%93length%E2%80%93value

    without LV encoding
    id | foo    | bar   |
    1  | ab     | c     | bytearray(ab) + bytearray(c)
    2  | a      | bc    | bytearray(a) + bytearray(bc)
    result: the two bytearrays from record 1 and record 2 are the same

    with LV encoding
    id | foo    | bar   |
    1  | ab     | c     | (lengthByte(2) + bytearray(ab)) + (lengthByte(1) + (bytearray(c))
    2  | a      | bc    | (lengthByte(1) + bytearray(a)) + (lengthByte(2) + bytearray(bc))
    result: the two bytearrays from record 1 and record 2 are NOT the same

    We also encode null values with a special marker (-1) to prevent collisions like:
    id | foo    | bar   |
    1  | null   | a     | bytearray(special_for_null) + (lengthByte(1) + bytearray(a))
    2  | a      | null  | (lengthByte(1) + bytearray(a)) + bytearray(special_for_null)

    bytearray(special_for_null) cannot be conflicted with other real values
     */
    return tableSignature.columns.fold(ByteArray(0)) { bytes, column ->
      when (val columnValue = ctx.record().get(column)) {
        // For null values, encode with -1 as a special marker (no value bytes follow)
        null -> {
          val nullMarker = ByteBuffer.allocate(4).putInt(-1).array()
          bytes + nullMarker
        }

        // For ByteArray values, prepend the length (4 bytes) then the value
        is ByteArray -> {
          val lengthBytes = ByteBuffer.allocate(4).putInt(columnValue.size).array()
          bytes + lengthBytes + columnValue
        }

        // For LocalDateTime, convert to bytes first, then apply Length-Value encoding
        is LocalDateTime -> {
          val precision = column.dataType.precision()
          val valueBytes = columnValue.toByteArray(precision)
          val lengthBytes = ByteBuffer.allocate(4).putInt(valueBytes.size).array()
          bytes + lengthBytes + valueBytes
        }
        // For all other types, convert to string, then to bytes, then apply Length-Value encoding
        else -> {
          val valueBytes = columnValue.toString().toByteArray()
          val lengthBytes = ByteBuffer.allocate(4).putInt(valueBytes.size).array()
          bytes + lengthBytes + valueBytes
        }
      }
    }
  }

  /**
   * MySQL's precision for a timestamp is millis. But in the Kube Pod, where the code runs the JVM timestamp is in
   * nanos. So when we store the data, the signature is computed with nanos, but when we load the data from the DB, the
   * nanos are lost and hence the signature computed is different. This method truncates the instant based on the
   * precision. The check with precision is required to be able to test this on a MAC. Mac JVM's precision is millis. So
   * in order to test truncation we need to create a mysql timestamp with a precision of 0. This also allows this
   * signature to work for any column created in prod where the precision is 0 (in the sense, restricted to store
   * seconds alone).
   */
  private fun LocalDateTime.toByteArray(precision: Int): ByteArray {
    return when {
      precision < 3 -> toInstant().truncatedTo(ChronoUnit.SECONDS).toEpochMilli().toString().toByteArray()
      else -> toInstant().truncatedTo(ChronoUnit.MILLIS).toEpochMilli().toString().toByteArray()
    }
  }

  private fun exceptionMessage(message: String, tableSignature: TableSignatureDetails, ctx: RecordContext): String {
    return message +
      " [Table=${tableSignature.table}] " +
      "[PK=${tableSignature.table.primaryKey?.fields?.map { ctx.record().get(it) }?.joinToString(", ")}]"
  }

  companion object {
    val log = getLogger<RecordSignatureListener>()
  }
}

data class TableSignatureDetails(
  /**
   * The key name used to create the HMAC signature More details here -
   * https://cash-dev-guide.squarecloudservices.com/security/key_management/ and
   * https://github.com/google/tink/blob/master/docs/PRIMITIVES.md
   */
  val signatureKeyName: String,
  /**
   * The columns that need to be protected against direct change in the database. Please note: the value of these
   * columns should be convertable deterministically into a string value or should be a byte array already such as BLOB
   * types. Most SQL value types can be converted into a string via toString() call. Note:
   * 1. JSON columns cannot be used as part of the signature columns as the string comparison of a JSON differs if there
   *    are whitespace differences. MYSQL does not store JSON as a string and hence when it is retrieved there usually
   *    are white space differences.
   * 2. If a timestamp column is used in the signature, remember that MYSQL's precision is limited to millis. The MAC
   *    JVM precision is limited to millis too. But the Kube pod where this is deployed has nano precision. So ensure
   *    the timestamp is truncated to millis before setting it into the record.
   */
  val columns: List<TableField<out Record, out Any?>>,
  /** The column where the HMAC signature (or hash) will be stored and then used to validate against */
  val signatureRecordColumn: TableField<out Record, ByteArray?>,
  /** The table that needs to be protected against direct change in the database. */
  val table: Table<out Record>,
  /**
   * When adding this listener to an existing table, set this flag to true until you are sure that all records in the
   * table have a signature set
   */
  val allowNullSignatures: Boolean,
)

class DataIntegrityException @JvmOverloads constructor(message: String, cause: Exception? = null) : DataAccessException(message, cause)
