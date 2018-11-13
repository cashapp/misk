package misk.hibernate

object ColumnValidator : MiskSchemaValidator<MiskColumn> {

  override fun process(errors: MiskSchemaErrors, dbSchema: MiskColumn, ormSchema: MiskColumn) {
    with(errors) {
      validate(dbSchema.normalizedName == dbSchema.name) {
        "Column ${dbSchema.name} should be in lower_snake_case"
      }
      validate(dbSchema.name == ormSchema.name) {
        "Column ${dbSchema.name} should exactly match hibernate ${ormSchema.name}"
      }

      // We have that the orm column only needs to be null if the database is null.
      // It's okay if hibernate is more strict.
      validate(dbSchema.nullable || !ormSchema.nullable) {
        "Column ${dbSchema.name} is NOT NULL in database but ${ormSchema.name} is nullable in hibernate"
      }
    }
  }
}
