plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.wispToken)
  api(project(":misk"))
  api(project(":misk-hibernate"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  api(project(":misk-transactional-jobqueue"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
}
