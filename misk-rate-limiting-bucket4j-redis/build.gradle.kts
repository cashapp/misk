import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(project(":misk-inject"))
  api(libs.bucket4jCore)

  implementation(project(":wisp:wisp-rate-limiting"))
  implementation(libs.guice)
  implementation(libs.jedis)
  implementation(libs.micrometerCore)
  implementation(project(":wisp:wisp-rate-limiting:bucket4j"))
  implementation(libs.bucket4jRedis)

  testImplementation(project(":misk"))
  testImplementation(project(":misk-rate-limiting-bucket4j-redis"))
  testImplementation(project(":misk-redis"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(testFixtures(project(":misk-redis")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)

  testFixturesImplementation(project(":misk-testing"))
  testFixturesImplementation(libs.bucket4jCore)
}

tasks.withType<Test>().configureEach {
  dependsOn(":startRedis")
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
