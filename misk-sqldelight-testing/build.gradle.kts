plugins {
  kotlin("jvm")
  id("app.cash.sqldelight")
}

dependencies {
  implementation(libs.sqldelightJdbcDriver)
}

sqldelight {
  databases {
    create("MoviesDatabase") {
      packageName.set("misk.sqldelight.testing")
      dialect(libs.sqldelightMysqlDialect)
      srcDirs("src/main/sqldelight", "src/main/resources/migrations")
      deriveSchemaFromMigrations.set(true)
      migrationOutputDirectory.set(file("$buildDir/resources/main/sqldelighttest"))
      verifyMigrations.set(true)
    }
  }
}

val compileKotlin by tasks.getting {
  dependsOn("generateMainMoviesDatabaseMigrations")
}
