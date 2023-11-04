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
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(Dependencies.jedis)
  api(Dependencies.micrometerCore)

  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-rate-limiting:bucket4j"))
  implementation(Dependencies.bucket4jCore)
  implementation(Dependencies.bucket4jRedis)

  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))

  testImplementation(project(":misk"))
  testImplementation(project(":misk-rate-limiting-bucket4j-redis"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-redis")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
