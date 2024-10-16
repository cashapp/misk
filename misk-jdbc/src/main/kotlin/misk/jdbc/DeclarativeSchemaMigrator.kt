package misk.jdbc

internal class DeclarativeSchemaMigrator : SchemaMigrator {
  override fun applyAll(author: String): MigrationStatus {
    throw UnsupportedOperationException("not implemented")
  }

  override fun requireAll(): MigrationStatus {
    throw UnsupportedOperationException("not implemented")
  }
}
