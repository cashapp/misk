plugins {
  kotlin("jvm")

  id("com.squareup.sqldelight")
  id("com.squareup.wire")
}

sourceSets {
  val main by getting {
    java.srcDir("src/main/kotlin/")
  }
}

dependencies {
  // TODO: these should be implementation("com.squareup.misk:*")
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-jdbc"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-sqldelight"))

  implementation(Dependencies.sqldelightJdbcDriver)

  testImplementation(project(":misk-testing"))
  testImplementation("org.assertj:assertj-core:3.23.1")
}

val jar by tasks.getting(Jar::class) {
  manifest {
    attributes("Main-Class" to "com.squareup.exemplar.ExemplarServiceKt")
  }
  classifier = "unshaded"
}

sourceSets {
  val main by getting {
    java.srcDir("$buildDir/generated/source/wire/")
  }
}

sqldelight {
  database("LibraryDatabase") {
    packageName = "com.squareup.library.db"
    sourceFolders = listOf("sqldelight", "resources/db-migrations")
    deriveSchemaFromMigrations = true
    migrationOutputDirectory = file("$buildDir/resources/main/db-migrations")
    dialect = "mysql"
    // TODO Add once sqlDelight bumped to 2.X
    // dialect = Dependencies.sqldelightMysqlDialect
  }
}

val compileKotlin by tasks.getting {
  dependsOn("generateMainLibraryDatabaseMigrations")
}


wire {
  sourcePath {
    srcDir("src/main/proto/")
  }

  kotlin {
    javaInterop = true
  }
}
