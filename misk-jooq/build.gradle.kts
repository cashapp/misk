import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("nu.studer.jooq")
  id("com.squareup.misk.schema-migrator")
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
  implementation(project(":misk-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk"))
  testImplementation(testFixtures(project(":misk-jdbc")))

  // Needed to generate jooq test db classes
  jooqGenerator(libs.mysql)
}

val dbMigrations = "src/test/resources/db-migrations"

val mysqlHost = System.getenv("MYSQL_HOST_DOCKER") ?: "localhost"
val mysqlPort = System.getenv("MYSQL_PORT") ?: "3500"

val mysqlDb = mapOf(
  "url" to "jdbc:mysql://$mysqlHost:$mysqlPort/",
  "schema" to "jooq",
  "user" to "root",
  "password" to "root"
)

miskSchemaMigrator {
  database = mysqlDb["schema"]
  host = mysqlHost
  port = mysqlPort.toInt()
  username = mysqlDb["user"]
  password = mysqlDb["password"]
  migrationsDir.set(layout.projectDirectory.dir(dbMigrations))
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
          url = "jdbc:mysql://$mysqlHost:$mysqlPort/${mysqlDb["schema"]}"
          user = mysqlDb["user"]
          password = mysqlDb["password"]
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.mysql.MySQLDatabase"
            inputSchema = "jooq"
            outputSchema = "jooq"
            includes = ".*"
            excludes = "(.*?schema_version)"
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
  dependsOn("migrateSchema")

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
