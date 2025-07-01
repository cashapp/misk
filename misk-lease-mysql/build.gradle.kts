import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  alias(libs.plugins.sqldelight)
  `java-test-fixtures`
}

// Override JVM target for detekt compatibility
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  jvmTarget = "21"
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":wisp:wisp-lease"))

  api(project(":misk-inject"))
  api(project(":misk-jdbc"))

  implementation(project(":misk-api"))
  implementation(project(":wisp:wisp-logging"))
  implementation(libs.sqldelightJdbcDriver)
  implementation(libs.loggingApi)

  testImplementation(project(":misk-api"))
  testImplementation(project(":misk-testing-api"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-lease-mysql")))
  testRuntimeOnly(libs.junitEngine)

  testFixturesImplementation(project(":misk-api"))
  testFixturesImplementation(project(":misk-inject"))
  testFixturesImplementation(testFixtures(project(":misk-jdbc")))

}

sqldelight {
  databases {
    create("LeaseDatabase") {
      dialect(libs.sqldelightMysqlDialect)
      packageName.set("misk.lease.mysql")
      srcDirs(listOf("src/main/sqldelight", "src/main/resources/sqldelight-migrations"))
      deriveSchemaFromMigrations.set(true)
      migrationOutputDirectory.set(file("$buildDir/resources/main/migrations"))
    }
  }
}

val compileKotlin by tasks.getting {
  dependsOn("generateMainLeaseDatabaseInterface")
}

tasks.jar {
  dependsOn("generateMainLeaseDatabaseMigrations")
}

tasks.test {
  dependsOn("generateMainLeaseDatabaseMigrations")
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
