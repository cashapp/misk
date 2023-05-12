plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-events-core"))
  api(project(":misk-hibernate"))
  implementation(libs.guava)
}
