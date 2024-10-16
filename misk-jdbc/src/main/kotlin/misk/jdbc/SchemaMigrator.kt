package misk.jdbc

interface SchemaMigrator {
  fun applyAll(author: String): MigrationStatus
  fun requireAll(): MigrationStatus
}

interface MigrationStatus {
  object Empty : MigrationStatus {
    override fun toString(): String {
      return "No migrations available to apply"
    }
  }

  fun message() = toString()
}
