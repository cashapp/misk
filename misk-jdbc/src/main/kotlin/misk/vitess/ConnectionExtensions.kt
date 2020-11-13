package misk.vitess

import misk.vitess.Shard.Companion.SINGLE_SHARD
import java.sql.Connection
import java.sql.SQLException

fun <T> Connection.target(shard: Shard, block: (conn: Connection) -> T): T =
    if (shard == SINGLE_SHARD) {
      // not vitess
      block(this)
    } else {
      target(currentTarget().mergedWith(Destination(shard))) { block(this) }
    }

private fun <T> Connection.target(destination: Destination, function: () -> T): T {
  val previous = currentTarget()
  targetDestination(previous.mergedWith(destination))
  try {
    return function()
  } finally {
    targetDestination(previous)
  }
}

private fun Connection.targetDestination(destination: Destination) =
    createStatement().use { statement ->
      val catalog = if (destination.isBlank()) "@master" else destination.toString()
      this.catalog = catalog
    }

private fun Connection.currentTarget(): Destination = Destination.parse(catalog)

/**
 * Runs a read on master first then tries it on replicas on failure. This method is here only for
 * health check purpose for standby regions.
 */
fun <T> Connection.failSafeRead(block: (conn: Connection) -> T): T =
    try {
      block(this)
    } catch (e: SQLException) {
      if (tabletDoesNotExists(e)) {
        target(Destination(TabletType.REPLICA)) {
          block(this)
        }
      } else {
        throw e
      }
    }

fun <T> Connection.failSafeRead(shard: Shard, block: (conn: Connection) -> T): T =
    failSafeRead { it.target(shard) { block(it) } }

fun tabletDoesNotExists(e: Exception): Boolean {
  val rootCause = getRootCause(e)
  val noMasterTabletRegex = ".*target:.*master.*no valid tablet:.*".toRegex(RegexOption.IGNORE_CASE)
  val isSQLException = rootCause is SQLException
  val isNoMasterTablet = noMasterTabletRegex.matches(rootCause.message!!)

  return isSQLException && isNoMasterTablet
}

fun getRootCause(throwable: Throwable): Throwable {
  var rootCause = throwable
  while (rootCause.cause != null && rootCause.cause != rootCause) {
    rootCause = rootCause.cause!!
  }
  return rootCause
}
