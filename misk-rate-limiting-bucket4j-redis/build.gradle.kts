import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  id("java-test-fixtures")
}

dependencies {
  api(project(":misk-inject"))
  implementation(project(":wisp:wisp-rate-limiting"))
  implementation(libs.guice)
  implementation(libs.jedis)
  implementation(libs.micrometerCore)

  implementation(project(":wisp:wisp-rate-limiting:bucket4j"))
  implementation(libs.bucket4jCore)
  implementation(libs.bucket4jRedis)

  testFixturesApi(project(":misk-testing"))

  testImplementation(project(":misk"))
  testImplementation(project(":misk-rate-limiting-bucket4j-redis"))
  testImplementation(project(":misk-redis"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(testFixtures(project(":misk-redis")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)

  testFixturesImplementation(project(":wisp:wisp-rate-limiting"))
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.jedis)
  testFixturesImplementation(libs.micrometerCore)
  testFixturesImplementation(project(":wisp:wisp-rate-limiting:bucket4j"))
  testFixturesImplementation(libs.bucket4jCore)
  testFixturesImplementation(libs.bucket4jRedis)
}

tasks.withType<Test> {
  dependsOn(":startRedis")
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
