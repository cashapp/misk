import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

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
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))
  implementation(project(":misk-crypto"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(project(":misk-jdbc"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.docker)
  // The docker-java we use in tests depends on an old version of junixsocket that depends on
  // log4j. We force it up a minor version in packages that use it.
  testImplementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.3.4") {
    isForce = true
  }
  testImplementation("com.kohlschutter.junixsocket:junixsocket-common:2.3.4") {
    isForce = true
  }
  testImplementation(Dependencies.prometheusClient)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":misk-metrics"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(project(":wisp-config"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
