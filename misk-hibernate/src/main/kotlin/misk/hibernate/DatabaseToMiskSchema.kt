package misk.hibernate

import java.sql.DatabaseMetaData

class DatabaseToMiskSchema(val databaseMetaData: DatabaseMetaData) {
  fun miskSchema(catalog: String?): MiskDatabase {
    val ignoreTables = arrayOf("schema_version")
    val tablesRs = fromDbMetadata { getTables(catalog, null, "%", null) }
    val schemaTables = mutableListOf<MiskTable>()

    while (tablesRs.next()) {
      val tableName = tablesRs.getString("TABLE_NAME")
      if (tableName in ignoreTables) continue
      val tableSchema: String? = tablesRs.getString("TABLE_SCHEM")
      val columnRs = fromDbMetadata { getColumns(catalog, tableSchema, tableName, "%") }
      val columns = mutableListOf<MiskColumn>()

      while (columnRs.next()) {
        columns += MiskColumn(
            columnRs.getString("COLUMN_NAME"),
            columnRs.getString("IS_NULLABLE") == "YES")
      }
      schemaTables += MiskTable(tableName, columns)
    }
    return MiskDatabase("databaseSchema", schemaTables)
  }

  private fun <R> fromDbMetadata(lambda: DatabaseMetaData.() -> R) = databaseMetaData.lambda()
}
