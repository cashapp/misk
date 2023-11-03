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
  api(project(":misk-redis"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-rate-limiting:bucket4j"))

  implementation(Dependencies.bucket4jRedis)
  implementation(project(":misk-service"))

  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))

  testImplementation(Dependencies.micrometerPrometheus)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-redis")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
