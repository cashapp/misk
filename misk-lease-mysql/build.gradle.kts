import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("app.cash.sqldelight")
  id("java-test-fixtures")
}

// Override JVM target for detekt compatibility
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  jvmTarget = "21"
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)

  api(project(":misk-inject"))
  api(project(":misk-jdbc"))

  implementation(project(":misk-api"))
  implementation(project(":wisp:wisp-lease"))
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

tasks.compileKotlin {
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
