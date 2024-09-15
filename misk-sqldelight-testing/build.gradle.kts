plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.sqldelight)
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
      migrationOutputDirectory.set(layout.buildDirectory.dir("resources/main/sqldelighttest"))
      verifyMigrations.set(true)
    }
  }
}

tasks.compileKotlin {
  dependsOn("generateMainMoviesDatabaseMigrations")
}
