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
  api(Dependencies.javaxInject)
  api(Dependencies.jedis)
  api(Dependencies.wispConfig)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.apacheCommonsPool2)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.prometheusClient)
  implementation(Dependencies.wispDeployment)
  implementation(project(":misk-service"))

  testFixturesApi(Dependencies.javaxInject)
  testFixturesApi(Dependencies.jedis)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(Dependencies.dockerApi)
  testFixturesImplementation(Dependencies.guava)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinLogging)
  testFixturesImplementation(Dependencies.okio)
  testFixturesImplementation(Dependencies.wispContainersTesting)
  testFixturesImplementation(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.wispTimeTesting)
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
