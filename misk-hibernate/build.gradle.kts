import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("org.jetbrains.kotlin.plugin.allopen")
  id("org.jetbrains.kotlin.plugin.jpa")
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
  api(libs.hibernate5Core)
  api(libs.jakartaInject)
  api(libs.hibernateJpaApi)
  api(libs.slf4jApi)
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  api(project(":misk-testing-api"))
  api(project(":misk-vitess"))
  api(project(":wisp:wisp-config"))
  implementation(libs.loggingApi)
  implementation(libs.kotlinReflect)
  implementation(libs.moshiCore)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.tink)
  implementation(libs.wireRuntime)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk"))
  implementation(project(":misk-api"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-audit-client"))
  implementation(project(":misk-backoff"))
  implementation(project(":misk-core"))
  implementation(project(":misk-crypto"))
  implementation(project(":misk-sampling"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.mockitoKotlin)
  testImplementation(libs.prometheusClient)
  testImplementation(project(":wisp:wisp-config"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(testFixtures(project(":misk-audit-client")))
  testImplementation(testFixtures(project(":misk-jdbc")))
  testImplementation(testFixtures(project(":misk-crypto")))
  testImplementation(testFixtures(project(":misk-vitess")))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
