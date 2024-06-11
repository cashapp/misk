import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  alias(libs.plugins.kotlinAllOpen)
  alias(libs.plugins.kotlinJpa)
}

allOpen {
  annotation("javax.persistence.Entity")
  annotation("javax.persistence.Embeddable")
  annotation("javax.persistence.MappedSuperclass")
}

sourceSets {
  test {
    java.srcDir("src/test/kotlin/")
  }
}

dependencies {
  api(libs.guava)
  api(libs.guice)
  api(libs.hibernateCore)
  api(libs.jakartaInject)
  api(libs.javaxPersistenceApi)
  api(libs.slf4jApi)
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  api(project(":wisp:wisp-config"))
  implementation(libs.kotlinLogging)
  implementation(libs.kotlinReflect)
  implementation(libs.moshiCore)
  implementation(libs.okHttp)
  implementation(libs.okio)
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
