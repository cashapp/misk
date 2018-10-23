package misk.vitess

import com.google.common.collect.ImmutableSet
import misk.jdbc.map
import misk.jdbc.uniqueResult
import java.sql.Connection

fun Connection.shards(): Set<Shard> {
  if (isVitess()) {
    return this.createStatement().use { s ->
      s.executeQuery("SHOW VITESS_SHARDS")
          .map { rs -> parseShard(rs.getString(1)) }
          .toSet()
    }
  } else {
    return SINGLE_SHARD_SET
  }
}

private fun Connection.isVitess() = this.isWrapperFor(Class.forName("io.vitess.jdbc.VitessConnection"))

private fun parseShard(string: String): Shard {
  val (keyspace, shard) = string.split('/', limit = 2)
  return Shard(Keyspace(keyspace), shard)
}

fun <T> Connection.target(shard: Shard, function: () -> T): T {
  if (isVitess()) {
    val previousTarget =
        createStatement().use { statement ->
          statement.executeQuery("SHOW VITESS_TARGET").uniqueResult { it.getString(1) }!!
        }
    createStatement().use { statement ->
      println("USE `$shard`")
      statement.execute("USE `$shard`")
    }
    try {
      return function()
    } finally {
      val sql = if (previousTarget.isBlank()) {
        "USE"
      } else {
        "USE `$previousTarget`"
      }
      println(sql)
      createStatement().use { it.execute(sql) }
    }
  } else {
    return function()
  }
}

val SINGLE_KEYSPACE = Keyspace("keyspace")
val SINGLE_SHARD = Shard(SINGLE_KEYSPACE, "0")
val SINGLE_SHARD_SET = ImmutableSet.of(SINGLE_SHARD)
