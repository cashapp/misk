package misk.hibernate

object TableValidator : MiskSchemaParentValidator<MiskColumn, MiskTable>() {

  override fun process(errors: MiskSchemaErrors, dbSchema: MiskTable, ormSchema: MiskTable) {
    val (dbOnly, ormOnly, intersectionPairs) = splitChildren(errors, dbSchema, ormSchema)

    with(errors) {
      validate(dbSchema.normalizedName == dbSchema.name){
        "Database table name \"${dbSchema.name}\" should be in lower_snake_case"
      }
      validate(dbSchema.name == ormSchema.name){
        "Database table name \"${dbSchema.name}\" should exactly match hibernate \"${ormSchema.name}\""
      }

      validate(ormOnly.isEmpty()) {
        "Database table \"${dbSchema.name}\" is missing columns ${ormOnly.map { it.name }} found in hibernate \"${ormSchema.name}\""
      }

      // TODO (maacosta) how strict should we be here? If `DEFAULT NULL` column exists in the Db and orm
      //                does not know about it orm can still do writes to db. However if we are going to do
      //                lookups on this column it might not work.. do we look at Queries.
      validate(dbOnly.isEmpty()) {
        "Hibernate entity \"${ormSchema.name}\" is missing columns ${dbOnly.map { it.name }} expected in table \"${dbSchema.name}\""
      }

      for ((dbColumn, ormColumn) in intersectionPairs) {
        ColumnValidator.process(newChildSchemaErrors(), dbColumn, ormColumn)
      }
    }
  }
}
