package misk.jdbc

import java.sql.ResultSet

fun <T> ResultSet.map(function: (ResultSet) -> T): List<T> {
  val result = mutableListOf<T>()
  while (this.next()) {
    result.add(function(this))
  }
  return result
}

fun <T> ResultSet.uniqueResult(function: (ResultSet) -> T): T? = map(function).firstOrNull()

fun ResultSet.uniqueString(): String = uniqueResult { it.getString(1) }!!

fun ResultSet.uniqueInt(): Int = uniqueResult { it.getInt(1) }!!
