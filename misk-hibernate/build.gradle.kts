import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
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
  api(libs.guava)
  api(libs.guice)
  api(libs.hibernateCore)
  api(libs.jakartaInject)
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
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk"))
  implementation(project(":misk-api"))
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
  testImplementation(project(":wisp:wisp-config"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(testFixtures(project(":misk-jdbc")))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-crypto")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
