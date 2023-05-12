plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  implementation(libs.guice)
  implementation(project(":misk-jdbc-testing"))
}
