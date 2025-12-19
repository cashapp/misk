package misk.vitess

data class Destination(val keyspace: Keyspace?, val shard: Shard?, val tabletType: TabletType?) {
  init {
    if (shard != null) {
      check(keyspace == shard.keyspace)
    }
  }

  constructor(shard: Shard) : this(shard.keyspace, shard, null)

  constructor(tabletType: TabletType) : this(null, null, tabletType)

  constructor(shard: Shard, tabletType: TabletType) : this(shard.keyspace, shard, tabletType)

  override fun toString(): String {
    val destinationQualifier = tabletType?.toDestinationQualifier() ?: ""
    return when {
      shard != null -> "$shard$destinationQualifier"
      keyspace != null -> "$keyspace$destinationQualifier"
      else -> destinationQualifier
    }
  }

  /** Merge the current Destination with another Destination, with the other Destination taking precedence. */
  fun mergedWith(other: Destination) =
    Destination(
      if (other.keyspace != null) other.keyspace else this.keyspace,
      if (other.shard != null) other.shard else this.shard,
      if (other.tabletType != null) other.tabletType else this.tabletType,
    )

  fun isBlank(): Boolean {
    return keyspace == null && shard == null && tabletType == null
  }

  companion object {
    /**
     * Parse a Destination from a catalog string. Examples:
     *
     * `@primary` -> should map to a Destination with `TabletType.PRIMARY` and no specific keyspace or shard `@replica`
     * -> should map to a Destination with `TabletType.REPLICA`, and no specific keyspace or shard `@master` -> should
     * map to a Destination with `TabletType.PRIMARY` (for backwards compatability) and no specific keyspace or shard
     * `ks/-80@primary` -> should map to a Destination with `TabletType.PRIMARY`, keyspace `ks`, and shard `-80`
     * `ks/-80@replica` -> should map to a Destination with `TabletType.REPLICA`, `keyspace `ks`, and shard `-80`
     * `ks/-80` -> should map to a Destination with no specific tablet type, keyspace `ks`, and shard -80 `ks` -> should
     * map to a Destination with keyspace `ks` and no specific shard and no tablet type `""` -> should map to a
     * Destination with no specific keyspace, shard, and tablet type
     */
    fun parse(catalogString: String): Destination {
      val qualifierIndex = catalogString.lastIndexOf('@')
      val qualifierSymbolFound = qualifierIndex != -1
      val keyspaceShardStr = if (!qualifierSymbolFound) catalogString else catalogString.take(qualifierIndex)
      val tabletType =
        if (!qualifierSymbolFound) null
        else TabletType.fromDestinationQualifier(catalogString.substring(qualifierIndex + 1))

      if (keyspaceShardStr.isEmpty()) {
        return Destination(null, null, tabletType)
      }

      val shard = Shard.parse(keyspaceShardStr)
      if (shard == null) {
        return Destination(Keyspace(keyspaceShardStr), null, tabletType)
      }

      return Destination(shard.keyspace, shard, tabletType)
    }

    /**
     * Return a `Destination` that targets `@primary` with no specific keyspace or shard. To get the destination string
     * of `@primary`, call `Destination.primary().toString()` or `"${Destination.primary()}"`
     *
     * @return `Destination` with `tabletType` set to `PRIMARY`
     */
    fun primary(): Destination {
      return Destination(tabletType = TabletType.PRIMARY)
    }

    /**
     * Return a `Destination` that targets `@replica` with no specific keyspace or shard. To get the destination string
     * of `@replica`, call `Destination.replica().toString()` or `"${Destination.replica()}"`
     *
     * @return `Destination` with `tabletType` set to `REPLICA`
     */
    fun replica(): Destination {
      return Destination(tabletType = TabletType.REPLICA)
    }
  }
}
