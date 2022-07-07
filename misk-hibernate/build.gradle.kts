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

sourceSets {
  val test by getting {
    java.srcDir("src/test/kotlin/")
  }
}

dependencies {
  api(Dependencies.hibernateCore)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.tink)
  implementation(project(":misk"))
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))
  implementation(project(":misk-crypto"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(project(":misk-jdbc"))
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.dockerCore)
  testImplementation(Dependencies.dockerTransport)
  testImplementation(Dependencies.prometheusClient)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":misk-metrics"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(Dependencies.wispConfig)
}
