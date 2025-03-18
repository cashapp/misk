package misk.vitess.testing.internal

/** Represents a Vitess keyspace with its tables and sharding information. */
internal data class VitessKeyspace(
  val name: String,
  val tables: List<VitessTable>,
  val sharded: Boolean,
  val shards: Int,
  val ddlCommands: List<Pair<String, String>>,
  val vschema: String,
)
