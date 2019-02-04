package misk.hibernate

internal class QueryTracingSpanNames {
  companion object {
    const val DB_SELECT = "db-select"
    const val DB_INSERT = "db-insert"
    const val DB_UPDATE = "db-update"
    const val DB_DELETE = "db-delete"
  }
}
