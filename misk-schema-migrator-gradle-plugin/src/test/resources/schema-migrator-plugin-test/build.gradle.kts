import java.util.Properties

plugins {
  id("misk.schema-migrator")
}

val properties = Properties().apply {
  val file = file("src/main/resources/db.properties")
  if (file.exists()) {
    load(file.inputStream())
  }
}

miskSchemaMigrator {
  database = properties.getProperty("schema")
  username = properties.getProperty("username")
  password = properties.getProperty("password")
  migrationsDir.set(layout.projectDirectory.dir("src/main/resources/db-migrations"))
}
