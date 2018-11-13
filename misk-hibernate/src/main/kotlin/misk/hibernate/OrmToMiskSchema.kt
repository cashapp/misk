package misk.hibernate

import org.hibernate.boot.Metadata

class OrmToMiskSchema(val metadata: Metadata) {

  fun miskSchema(): MiskDatabase {
    val metadata: Metadata = MetadataIntegrator.metadata
    val hibernateTables = mutableListOf<MiskTable>()
    val tableIt = metadata.collectTableMappings().iterator()
    while (tableIt.hasNext()) {
      val table = tableIt.next()
      val tableName = table.name

      val columnsIt = table.columnIterator
      val columns = mutableListOf<MiskColumn>()
      while (columnsIt.hasNext()) {
        val column = columnsIt.next() as org.hibernate.mapping.Column
        columns += MiskColumn(column.name, column.isNullable)
      }

      hibernateTables += MiskTable(tableName, columns)
    }
    return MiskDatabase("hibernateSchema", hibernateTables)
  }
}
