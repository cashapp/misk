package misk.jdbc

import java.sql.ResultSet

fun <T> ResultSet.map(function: (ResultSet) -> T): List<T> {
  val result = mutableListOf<T>()
  while (this.next()) {
    result.add(function(this))
  }
  return result
}

fun <T> ResultSet.maybeResult(function: (ResultSet) -> T): T? = map(function).firstOrNull()

fun <T> ResultSet.uniqueResult(function: (ResultSet) -> T): T = map(function).first()

fun ResultSet.uniqueString(): String = uniqueResult { it.getString(1) }

fun ResultSet.maybeString(): String? = maybeResult { it.getString(1) }

fun ResultSet.uniqueInt(): Int = uniqueResult { it.getInt(1) }

fun ResultSet.maybeInt(): Int? = maybeResult { it.getInt(1) }

fun ResultSet.uniqueLong(): Long = uniqueResult { it.getLong(1) }

fun ResultSet.maybeLong(): Long? = maybeResult { it.getLong(1) }
