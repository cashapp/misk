# Schema Migrator Gradle Plugin

This module provides a Gradle plugin for managing schema migrations. It uses the Misk schema 
migrator that runs in local development and testing, and packages it into a Gradle plugin that can
run standalone. This is useful for when you want to run your schema migrations without running your
service, for instance if you are generating code with JOOQ. It can be used as an alternative to
Flyway.

## Usage

```
plugins {
  id("misk.schemamigratorgradleplugin") version <<latest version>>
}

val dbConfig = mapOf(
  "url" to "jdbc:mysql://localhost:3306/",
  "schema" to "codegen",
  "user" to "root",
  "password" to ""
)

miskSchemaMigrator {
  database = dbConfig["schema"]
  username = dbConfig["user"]
  password = dbConfig["password"]
  schemaDir.set(layout.projectDirectory.dir("src/main/resources/db-migrations"))
}

// If you want to integrate with JOOQ
// tasks.withType<nu.studer.gradle.jooq.JooqGenerate>().configureEach {
//   dependsOn("migrateSchemas")
// }
```