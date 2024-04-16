import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.jedis)
  api(libs.guice)
  api(project(":wisp:wisp-config"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.apacheCommonsPool2)
  implementation(libs.okio)
  implementation(libs.prometheusClient)
  implementation(project(":wisp:wisp-logging"))
  implementation(libs.kotlinLogging)
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":misk-service"))

  testFixturesApi(libs.jedis)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(libs.dockerApi)
  testFixturesImplementation(libs.guava)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.kotlinLogging)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(project(":wisp:wisp-containers-testing"))
  testFixturesImplementation(project(":wisp:wisp-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-redis"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-redis")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
