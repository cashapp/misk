import misk.vitess.gradle.StartVitessDatabaseTask

plugins {
  id("com.squareup.misk.vitess.vitess-database")
}

val startVitessDatabase = tasks.named("startVitessDatabase", StartVitessDatabaseTask::class.java) {
  containerName.set("plugin_test_vitess_db")
  lintSchema.set(true)
  port.set(31503)
  schemaDir.set("filesystem:${layout.projectDirectory.dir("src/main/resources/vitess/schema")}")
}
