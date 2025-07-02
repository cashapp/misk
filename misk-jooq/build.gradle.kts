import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")

  // Needed to generate jooq test db classes
  id("org.flywaydb.flyway")
  id("nu.studer.jooq")
}

dependencies {
  api(libs.guava)
  api(libs.guice)
  api(libs.jooq)
  api(libs.loggingApi)
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  implementation(libs.jakartaInject)
  implementation(project(":misk-backoff"))
  implementation(project(":wisp:wisp-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk"))
  testImplementation(testFixtures(project(":misk-jdbc")))

  // Needed to generate jooq test db classes
  jooqGenerator(libs.mysql)
}

val dbMigrations = "src/test/resources/db-migrations"

// Needed to generate jooq test db classes
flyway {
  url = "jdbc:mysql://localhost:3500/misk-jooq-test-codegen"
  user = "root"
  password = "root"
  schemas = arrayOf("jooq")
  locations = arrayOf("filesystem:${project.projectDir}/${dbMigrations}")
  sqlMigrationPrefix = "v"
}
// Needed to generate jooq test db classes
jooq {
  edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

  configurations {
    create("main") {
      generateSchemaSourceOnCompilation.set(false)
      jooqConfiguration.apply {
        jdbc.apply {
          driver = "com.mysql.cj.jdbc.Driver"
          url = "jdbc:mysql://localhost:3500/misk-jooq-test-codegen"
          user = "root"
          password = "root"
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.mysql.MySQLDatabase"
            inputSchema = "jooq"
            includes = ".*"
            excludes = "(.*?FLYWAY_SCHEMA_HISTORY)|(.*?schema_version)"
            recordVersionFields = "version"
          }
          generate.apply {
            isJavaTimeTypes = true
          }
          target.apply {
            packageName = "misk.jooq.testgen"
            directory   = "${project.projectDir}/src/test/generated/kotlin"
          }
        }
      }
    }
  }
}

// Needed to generate jooq test db classes
tasks.withType<nu.studer.gradle.jooq.JooqGenerate>().configureEach {
  dependsOn("flywayMigrate")

  // declare migration files as inputs on the jOOQ task and allow it to
  // participate in build caching
  inputs.files(fileTree(layout.projectDirectory.dir(dbMigrations)))
    .withPropertyName("migrations")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  allInputsDeclared.set(true)
}

// Needed to generate jooq test db classes
// If you are using this as an example for your service, remember to add the generated code to your
// main source set instead of your tests as it is done below.
sourceSets {
  test {
    java.srcDirs(layout.projectDirectory.dir("src/test/generated/kotlin"))
  }
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
