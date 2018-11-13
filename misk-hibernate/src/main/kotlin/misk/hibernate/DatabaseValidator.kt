package misk.hibernate

object DatabaseValidator : MiskSchemaParentValidator<MiskTable, MiskDatabase>() {

  override fun process(errors: MiskSchemaErrors, dbSchema: MiskDatabase, ormSchema: MiskDatabase) {
    with(errors) {
      val (dbOnly, ormOnly, intersectionPairs) = splitChildren(errors, dbSchema, ormSchema)

      validate(ormOnly.isEmpty()) {
        "The ${dbSchema.name} is missing tables ${ormOnly.map { it.name }}"
      }

      validate(dbOnly.isEmpty()) {
        "The ${ormSchema.name} is missing tables ${dbOnly.map { it.name }}"
      }

      for ((dbTable, ormTable) in intersectionPairs) {
        info("Comparing tables ${ormTable.name} and ${dbTable.name}:")
        TableValidator.process(newChildSchemaErrors(), dbTable, ormTable)
      }
    }
  }
}
