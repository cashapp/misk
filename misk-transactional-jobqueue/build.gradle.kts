plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":misk-core"))
  implementation(project(":misk-jobqueue"))
  implementation(project(":misk-hibernate"))

  testImplementation(project(":misk-testing"))
}
