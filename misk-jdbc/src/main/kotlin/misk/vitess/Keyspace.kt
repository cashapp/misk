package misk.vitess

data class Keyspace(val name: String) {
  init {
    checkValidShardIdentifier(name)
  }

  override fun toString(): String = name
}
