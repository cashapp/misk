@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  `java-library`
  
  // Needed to generate jooq test db classes
  alias(libs.plugins.flywayPlugin)
  alias(libs.plugins.jooqPlugin)
}

dependencies {
  api(libs.javaxInject)
  api(libs.jooq)
  api(libs.kotlinLogging)
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.kotlinRetry)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.wispLogging)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.wispDeployment)
  testImplementation(libs.wispTimeTesting)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-jdbc-testing"))
  testImplementation(project(":misk-testing"))

  // Needed to generate jooq test db classes
  jooqGenerator(libs.mysql)
}

// Needed to generate jooq test db classes
flyway {
  url = "jdbc:mysql://localhost:3500/misk-jooq-test-codegen"
  user = "root"
  password = "root"
  schemas = arrayOf("jooq")
  locations = arrayOf("filesystem:${project.projectDir}/src/test/resources/db-migrations")
  sqlMigrationPrefix = "v"
}

// Needed to generate jooq test db classes
jooq {
  version.set(libs.jooq.get().version)
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
            outputSchema = "jooq"
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
val generateJooq by project.tasks
generateJooq.dependsOn("flywayMigrate")

// Needed to generate jooq test db classes
// If you are using this as an example for your service, remember to add the generated code to your
// main source set instead of your tests as it is done below.
sourceSets.getByName("test").java.srcDirs
  .add(File("${project.projectDir}/src/test/generated/kotlin"))
