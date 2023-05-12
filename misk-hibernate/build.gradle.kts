import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  `java-library`

  alias(libs.plugins.kotlinAllOpenPlugin)
  alias(libs.plugins.kotlinJpaPlugin)
}

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
  api(libs.guice)
  api(libs.hibernateCore)
  api(libs.javaxInject)
  api(libs.javaxPersistenceApi)
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  implementation(libs.kotlinLogging)
  implementation(libs.kotlinReflect)
  implementation(libs.moshi)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.slf4jApi)
  implementation(libs.tink)
  implementation(libs.tinkAwskms)
  implementation(libs.tinkGcpkms)
  implementation(libs.wireRuntime)
  implementation(libs.wispLogging)
  implementation(project(":misk"))
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))
  implementation(project(":misk-crypto"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.prometheusClient)
  testImplementation(libs.wispConfig)
  testImplementation(libs.wispDeployment)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(libs.wispTimeTesting)
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-crypto-testing"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(project(":misk-jdbc-testing"))
  testImplementation(project(":misk-metrics"))
  testImplementation(project(":misk-testing"))
}
