plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-testing"))
}
