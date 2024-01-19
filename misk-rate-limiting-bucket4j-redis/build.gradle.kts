import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(project(":misk-inject"))
  api(project(":wisp:wisp-rate-limiting"))
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.jedis)
  api(libs.micrometerCore)

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
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
