plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-hibernate"))
  api(project(":misk-jobqueue"))
}
