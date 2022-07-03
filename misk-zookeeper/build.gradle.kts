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
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.curatorFramework) {
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
    exclude(group = "log4j", module = "log4j")
  }
  implementation(Dependencies.zookeeper) {
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
    exclude(group = "log4j", module = "log4j")
  }
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(Dependencies.wispConfig)
  api(Dependencies.wispLease)
  api(Dependencies.wispLeaseTesting)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.dockerCore)
  testImplementation(Dependencies.dockerTransport)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.zookeeper) {
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
    exclude(group = "log4j", module = "log4j")
  }
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-zookeeper-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
}

sourceSets {
  val test by getting {
    resources {
      srcDir("../misk-zookeeper-testing/src/main/resources")
    }
  }
}
