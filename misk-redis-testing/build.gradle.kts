import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
  kotlin("jvm")
  `java-library`
}

apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
apply(plugin = "kotlin-jpa")

configure<AllOpenExtension> {
  annotation("javax.persistence.Entity")
  annotation("javax.persistence.Embeddable")
  annotation("javax.persistence.MappedSuperclass")
}

dependencies {
  api(Dependencies.wispConfig)
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okio)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-redis"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk-metrics-testing"))

}
