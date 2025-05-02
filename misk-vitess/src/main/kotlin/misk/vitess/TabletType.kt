package misk.vitess

enum class TabletType(val value: Int) {
  PRIMARY(1),
  REPLICA(2);

  fun toDestinationQualifier(): String = "@${name.lowercase()}"

  companion object {
    private val destinationQualifierToTabletType = mapOf(
      "primary" to PRIMARY,
      "master" to PRIMARY, // `@master` is deprecated, but we support it for backwards compatibility
      "replica" to REPLICA
    )

    fun fromDestinationQualifier(destinationQualifier: String): TabletType {
      return destinationQualifierToTabletType[destinationQualifier.lowercase()]
        ?: throw IllegalArgumentException("Invalid destination qualifier: `$destinationQualifier`, must be one of: [`@primary`, `@replica`]")
    }
  }
}
