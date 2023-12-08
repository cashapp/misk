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
  api(Dependencies.guava)
  api(Dependencies.jakartaInject)
  api(Dependencies.jedis)
  api(project(":wisp:wisp-config"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.apacheCommonsPool2)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.prometheusClient)
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.jedis)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(Dependencies.dockerApi)
  testFixturesImplementation(Dependencies.guava)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinLogging)
  testFixturesImplementation(Dependencies.okio)
  testFixturesImplementation(project(":wisp:wisp-containers-testing"))
  testFixturesImplementation(project(":wisp:wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
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
