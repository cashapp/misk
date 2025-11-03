# Vitess Database Gradle Plugin

This module provides a Gradle plugin for starting a Vitess test database. It serves as a wrapper around [VitessTestDb](../misk-vitess/README.md#vitesstestdb) and automatically processes schema changes / database migrations.

## Usage

For simple usage, update your build.gradle.kts file with the following:

```
plugins {
  id("com.squareup.misk.vitess.vitess-database")
}

tasks.withType<Test>().configureEach {
  // Start the Vitess test database before running tests
  dependsOn("startVitessDatabase")
}
```

To customize Vitess test database properties:

```
import misk.vitess.gradle.StartVitessDatabaseTask

plugins {
  id("com.squareup.misk.vitess.vitess-database")
}

val startVitessDatabase = tasks.named("startVitessDatabase", StartVitessDatabaseTask::class.java) {
  containerName.set("my_vitess_db")
  lintSchema.set(true)
  mysqlVersion.set("8.0.42")
  vitessImage.set("vitess/vttestserver:v19.0.9-mysql80")
}

tasks.withType<Test>().configureEach {
  // Start the Vitess test database before running tests
  dependsOn(startVitessDatabase)
```