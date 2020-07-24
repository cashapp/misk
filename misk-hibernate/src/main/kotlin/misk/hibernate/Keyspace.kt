package misk.hibernate

data class Keyspace(val name: String) {
  init {
    checkValidShardIdentifier(name)
  }

  override fun toString(): String = name
}
