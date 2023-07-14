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
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.hibernateCore)
  api(Dependencies.javaxInject)
  api(Dependencies.javaxPersistenceApi)
  api(project(":misk:misk-inject"))
  api(project(":misk:misk-jdbc"))
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.moshi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.tink)
  implementation(Dependencies.tinkAwskms)
  implementation(Dependencies.tinkGcpkms)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk:misk"))
  implementation(project(":misk:misk-action-scopes"))
  implementation(project(":misk:misk-actions"))
  implementation(project(":misk:misk-admin"))
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-crypto"))
  implementation(project(":misk:misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.prometheusClient)
  testImplementation(Dependencies.wispConfig)
  testImplementation(Dependencies.wispDeployment)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk:misk-config"))
  testImplementation(project(":misk:misk-hibernate-testing"))
  testImplementation(project(":misk:misk-jdbc-testing"))
  testImplementation(project(":misk:misk-metrics"))
  testImplementation(project(":misk:misk-testing"))
  testImplementation(testFixtures(project(":misk:misk-crypto")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
