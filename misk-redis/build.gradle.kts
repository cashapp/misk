import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  id("java-test-fixtures")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.jedis)
  api(project(":wisp:wisp-config"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.apacheCommonsPool)
  implementation(libs.guice)
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
