package misk.vitess

import misk.hibernate.checkValidShardIdentifier

data class Keyspace(val name: String) {
  init {
    checkValidShardIdentifier(name)
  }
}