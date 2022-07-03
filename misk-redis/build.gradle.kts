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
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okio)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(Dependencies.wispConfig)

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}
