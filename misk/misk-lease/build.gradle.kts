plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.wispLease)
  implementation(Dependencies.guice)
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
}
