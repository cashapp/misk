package misk.vitess

data class Destination(
  val keyspace: Keyspace?,
  val shard: Shard?,
  val tabletType: TabletType?
) {
  init {
    if (shard != null) {
      check(keyspace == shard.keyspace)
    }
  }

  constructor(shard: Shard) : this(shard.keyspace, shard, null)

  constructor(tabletType: TabletType) : this(null, null, tabletType)

  constructor(shard: Shard, tabletType: TabletType) : this(shard.keyspace, shard, tabletType)

  override fun toString(): String {
    val tabletType = (if (tabletType != null) "@$tabletType" else "").toLowerCase()
    if (shard != null) {
      return "$shard$tabletType"
    }
    if (keyspace != null) {
      return "$keyspace$tabletType"
    }
    return tabletType
  }

  fun mergedWith(other: Destination) =
      Destination(
          if (other.keyspace != null) other.keyspace else this.keyspace,
          if (other.shard != null) other.shard else this.shard,
          if (other.tabletType != null) other.tabletType else this.tabletType
      )

  fun isBlank(): Boolean {
    return keyspace == null && shard == null && tabletType == null
  }

  companion object {
    fun parse(string: String): Destination {
      val index = string.lastIndexOf('@')
      val shardStr = if (index == -1) {
        string
      } else {
        string.substring(0, index)
      }
      val tabletType = if (index == -1) {
        null
      } else {
        TabletType.valueOf(string.substring(index + 1).toUpperCase())
      }
      if (shardStr == "") {
        return Destination(null, null, tabletType)
      } else {
        val shard = Shard.parse(shardStr)
        return Destination(shard.keyspace, shard, tabletType)
      }
    }
  }
}
