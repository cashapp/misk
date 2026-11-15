package misk.jdbc

interface SchemaMigrator {

  /** Applies available migration to the database. */
  fun applyAll(author: String): MigrationStatus

  /** Validates that all migrations have been applied to the database. */
  fun requireAll(): MigrationStatus
}

interface MigrationStatus {
  object Empty : MigrationStatus {
    override fun toString(): String {
      return "No migrations available to apply"
    }
  }

  object Success : MigrationStatus {
    override fun toString(): String {
      return "All migrations have been applied"
    }
  }

  fun message() = toString()
}
