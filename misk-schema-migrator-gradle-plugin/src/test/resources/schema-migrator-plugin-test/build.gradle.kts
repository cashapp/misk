import java.util.Properties

plugins {
  id("com.squareup.misk.schema-migrator")
}

val properties = Properties().apply {
  val file = file("src/main/resources/db.properties")
  if (file.exists()) {
    load(file.inputStream())
  }
}

miskSchemaMigrator {
  host = "localhost"
  port = 3306
  database = properties.getProperty("schema")
  username = properties.getProperty("username")
  password = properties.getProperty("password")
  migrationsDir.set(layout.projectDirectory.dir("src/main/resources/db-migrations"))
}
