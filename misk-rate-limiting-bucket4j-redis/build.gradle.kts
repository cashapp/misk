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
  api(project(":misk-metrics"))
  api(project(":misk-redis"))
  api(project(":wisp:wisp-deployment"))
  api(project(":wisp:wisp-rate-limiting"))
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.jedis)
  api(libs.micrometerCore)

  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-rate-limiting:bucket4j"))
  implementation(libs.bucket4jCore)
  implementation(libs.bucket4jRedis)

  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))

  testImplementation(project(":misk"))
  testImplementation(project(":misk-rate-limiting-bucket4j-redis"))
  testImplementation(project(":misk-testing"))
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
